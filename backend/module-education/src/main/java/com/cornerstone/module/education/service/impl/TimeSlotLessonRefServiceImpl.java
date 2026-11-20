package com.cornerstone.module.education.service.impl;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.TimeSlotLessonCleanupResponse;
import com.cornerstone.module.education.dto.TimeSlotLessonRefView;
import com.cornerstone.module.education.mapper.AttendanceMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.TimeSlotLessonRefService;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeSlotLessonRefServiceImpl implements TimeSlotLessonRefService {

    private final TimeSlotMapper timeSlotMapper;
    private final ScheduleLessonMapper lessonMapper;
    private final ScheduleTeacherRelMapper teacherRelMapper;
    private final AttendanceMapper attendanceMapper;
    private final TeachingClassMapper classMapper;

    public TimeSlotLessonRefServiceImpl(TimeSlotMapper timeSlotMapper, ScheduleLessonMapper lessonMapper,
                                        ScheduleTeacherRelMapper teacherRelMapper, AttendanceMapper attendanceMapper,
                                        TeachingClassMapper classMapper) {
        this.timeSlotMapper = timeSlotMapper;
        this.lessonMapper = lessonMapper;
        this.teacherRelMapper = teacherRelMapper;
        this.attendanceMapper = attendanceMapper;
        this.classMapper = classMapper;
    }

    @Override
    public List<TimeSlotLessonRefView> listRefsByTimeSlot(Long operatorInstitutionId, Long timeSlotId) {
        requireTimeSlot(operatorInstitutionId, timeSlotId);
        // 机构通用节次被各校区共用，引用列表仍需按可访问校区过滤，避免看到其他校区的课次
        List<ScheduleLessonEntity> lessons = lessonMapper.selectRefsByTimeSlot(operatorInstitutionId, timeSlotId)
            .stream()
            .filter(lesson -> CampusScopeGuard.canAccess(lesson.getCampusId()))
            .toList();
        Map<Long, String> classNames = classNames(lessons);
        return lessons.stream()
            .map(lesson -> new TimeSlotLessonRefView(lesson.getId(), lesson.getLessonDate(), lesson.getLessonWeekday(),
                lesson.getStartTime(), lesson.getEndTime(), lesson.getClassId(),
                classNames.get(lesson.getClassId()), lesson.getLessonStatus(),
                Boolean.TRUE.equals(lesson.getDeleted())))
            .toList();
    }

    @Override
    @Transactional
    public TimeSlotLessonCleanupResponse purgeTombstoneLessons(Long operatorInstitutionId, Long timeSlotId) {
        TimeSlotEntity timeSlot = requireTimeSlot(operatorInstitutionId, timeSlotId);
        // 清理是物理删除，机构通用节次牵涉所有校区，只有不受限角色能操作
        CampusScopeGuard.assertNullableCampus(timeSlot.getCampusId(), "节次课次清理");
        List<ScheduleLessonEntity> tombstones = lessonMapper.selectRefsByTimeSlot(operatorInstitutionId, timeSlotId)
            .stream()
            .filter(lesson -> Boolean.TRUE.equals(lesson.getDeleted()))
            .toList();
        long purged = 0;
        long skipped = 0;
        for (ScheduleLessonEntity lesson : tombstones) {
            if (lessonMapper.countLiveMakeupRefs(lesson.getId()) > 0) {
                // 未删除的补课课次还指着它，删了会让补课记录悬空，这类墓碑留给排课页处理
                skipped++;
                continue;
            }
            // 引用它的只可能是同样已删除的补课课次，先断开再删本体，否则外键拦（1451）
            lessonMapper.clearMakeupRefs(lesson.getId());
            attendanceMapper.physicalDeleteByLessonId(lesson.getId());
            teacherRelMapper.physicalDeleteByLessonId(lesson.getId());
            lessonMapper.deleteTombstonePhysically(lesson.getId());
            purged++;
        }
        return new TimeSlotLessonCleanupResponse(purged, skipped,
            timeSlotMapper.countLiveLessonRefs(timeSlotId));
    }

    /** 班级名一次性批量取，班级本身已删除时留空由前端回显「班级 #id」 */
    private Map<Long, String> classNames(List<ScheduleLessonEntity> lessons) {
        List<Long> classIds = lessons.stream()
            .map(ScheduleLessonEntity::getClassId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return classMapper.selectBatchIds(classIds).stream()
            .filter(item -> item.getId() != null && item.getClassName() != null)
            .collect(Collectors.toMap(TeachingClassEntity::getId, TeachingClassEntity::getClassName,
                (first, second) -> first));
    }

    private TimeSlotEntity requireTimeSlot(Long operatorInstitutionId, Long timeSlotId) {
        TimeSlotEntity timeSlot = timeSlotMapper.selectById(timeSlotId);
        if (timeSlot == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节次不存在：" + timeSlotId);
        }
        if (!Objects.equals(timeSlot.getInstitutionId(), operatorInstitutionId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他机构的节次");
        }
        return timeSlot;
    }
}
