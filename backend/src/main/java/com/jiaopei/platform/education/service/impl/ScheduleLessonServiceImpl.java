package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.ScheduleLessonEntity;
import com.jiaopei.platform.education.domain.entity.ScheduleTeacherRelEntity;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.ScheduleConflictCheckRequest;
import com.jiaopei.platform.education.dto.ScheduleConflictCheckResponse;
import com.jiaopei.platform.education.dto.ScheduleLessonRequest;
import com.jiaopei.platform.education.dto.ScheduleLessonView;
import com.jiaopei.platform.education.mapper.ScheduleLessonMapper;
import com.jiaopei.platform.education.mapper.ScheduleTeacherRelMapper;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.ScheduleLessonService;
import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.organization.domain.entity.TimeSlotEntity;
import com.jiaopei.platform.organization.mapper.ClassroomMapper;
import com.jiaopei.platform.organization.mapper.TimeSlotMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleLessonServiceImpl implements ScheduleLessonService {

    private static final String SCHEDULED = "scheduled";
    private static final String RESCHEDULED = "rescheduled";
    private static final String CANCELLED = "cancelled";
    private static final String MAKEUP = "makeup";
    private static final String COMPLETED = "completed";
    private static final String CANCELLED_MAKEUP = "cancelled_makeup";

    private final ScheduleLessonMapper lessonMapper;
    private final ScheduleTeacherRelMapper teacherRelMapper;
    private final TeachingClassMapper classMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final TimeSlotMapper timeSlotMapper;

    public ScheduleLessonServiceImpl(ScheduleLessonMapper lessonMapper, ScheduleTeacherRelMapper teacherRelMapper,
                                     TeachingClassMapper classMapper, TeacherMapper teacherMapper,
                                     ClassroomMapper classroomMapper, TimeSlotMapper timeSlotMapper) {
        this.lessonMapper = lessonMapper;
        this.teacherRelMapper = teacherRelMapper;
        this.classMapper = classMapper;
        this.teacherMapper = teacherMapper;
        this.classroomMapper = classroomMapper;
        this.timeSlotMapper = timeSlotMapper;
    }

    @Override
    public List<ScheduleLessonView> listLessons(Long institutionId, Long campusId, Long classId, LocalDate lessonDate,
                                                Boolean pendingMakeupOnly) {
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = baseOrder();
        if (institutionId != null) {
            wrapper.eq(ScheduleLessonEntity::getInstitutionId, institutionId);
        }
        if (campusId != null) {
            wrapper.eq(ScheduleLessonEntity::getCampusId, campusId);
        }
        if (classId != null) {
            wrapper.eq(ScheduleLessonEntity::getClassId, classId);
        }
        if (lessonDate != null) {
            wrapper.eq(ScheduleLessonEntity::getLessonDate, lessonDate);
        }
        return toViews(lessonMapper.selectList(wrapper), pendingMakeupOnly);
    }

    @Override
    public ScheduleLessonView getLesson(Long id) {
        return toViews(List.of(requireLesson(id)), Boolean.FALSE).getFirst();
    }

    @Override
    @Transactional
    public ScheduleLessonView createLesson(ScheduleLessonRequest request) {
        ScheduleMaterial material = validateMaterial(request, null);
        assertNoConflicts(request, null);
        ScheduleLessonEntity entity = toEntity(request, material);
        entity.setDeleted(Boolean.FALSE);
        lessonMapper.insert(entity);
        replaceTeachers(entity.getId(), request.teacherIds());
        return toViews(List.of(entity), Boolean.FALSE).getFirst();
    }

    @Override
    @Transactional
    public ScheduleLessonView updateLesson(Long id, ScheduleLessonRequest request) {
        requireLesson(id);
        ScheduleMaterial material = validateMaterial(request, id);
        assertNoConflicts(request, id);
        ScheduleLessonEntity entity = toEntity(request, material);
        entity.setId(id);
        lessonMapper.updateById(entity);
        replaceTeachers(id, request.teacherIds());
        return toViews(List.of(requireLesson(id)), Boolean.FALSE).getFirst();
    }

    @Override
    @Transactional
    public void deleteLesson(Long id) {
        requireLesson(id);
        teacherRelMapper.physicalDeleteByLessonId(id);
        lessonMapper.deleteById(id);
    }

    @Override
    public List<ScheduleLessonView> dayTimetable(Long institutionId, Long campusId, Long classId, LocalDate lessonDate,
                                                 Boolean pendingMakeupOnly) {
        if (lessonDate == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上课日期不能为空");
        }
        return listLessons(institutionId, campusId, classId, lessonDate, pendingMakeupOnly);
    }

    @Override
    public List<ScheduleLessonView> weekTimetable(Long institutionId, Long campusId, Long classId, LocalDate weekStart,
                                                  LocalDate weekEnd, Boolean pendingMakeupOnly) {
        if (weekStart == null || weekEnd == null || weekStart.isAfter(weekEnd)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "周起始日期和周结束日期必须为有效日期，且周起始日期不能晚于周结束日期");
        }
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = baseOrder()
            .between(ScheduleLessonEntity::getLessonDate, weekStart, weekEnd);
        if (institutionId != null) {
            wrapper.eq(ScheduleLessonEntity::getInstitutionId, institutionId);
        }
        if (campusId != null) {
            wrapper.eq(ScheduleLessonEntity::getCampusId, campusId);
        }
        if (classId != null) {
            wrapper.eq(ScheduleLessonEntity::getClassId, classId);
        }
        return toViews(lessonMapper.selectList(wrapper), pendingMakeupOnly);
    }

    @Override
    public ScheduleConflictCheckResponse checkConflicts(ScheduleConflictCheckRequest request) {
        ScheduleLessonRequest lessonRequest = new ScheduleLessonRequest(
            request.institutionId(),
            request.campusId(),
            request.termId(),
            request.classId(),
            request.classroomId(),
            request.timeSlotId(),
            request.lessonDate(),
            null,
            SCHEDULED,
            null,
            null,
            request.teacherIds(),
            null
        );
        validateMaterial(lessonRequest, null);
        return detectConflicts(lessonRequest, request.excludeLessonId());
    }

    private ScheduleMaterial validateMaterial(ScheduleLessonRequest request, Long currentLessonId) {
        TeachingClassEntity teachingClass = classMapper.selectById(request.classId());
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + request.classId());
        }
        if (!Objects.equals(teachingClass.getInstitutionId(), request.institutionId())
            || !Objects.equals(teachingClass.getTermId(), request.termId())
            || !Objects.equals(teachingClass.getCampusId(), request.campusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级的学期/校区/机构与课次不一致");
        }
        ClassroomEntity classroom = classroomMapper.selectById(request.classroomId());
        if (classroom == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "教室不存在：" + request.classroomId());
        }
        if (!Objects.equals(classroom.getCampusId(), request.campusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "教室校区与课次不一致");
        }
        TimeSlotEntity timeSlot = timeSlotMapper.selectById(request.timeSlotId());
        if (timeSlot == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "时间段不存在：" + request.timeSlotId());
        }
        if (!Objects.equals(timeSlot.getInstitutionId(), request.institutionId())
            || (timeSlot.getCampusId() != null && !Objects.equals(timeSlot.getCampusId(), request.campusId()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "时间段与课次不匹配");
        }
        Integer weekday = request.lessonDate().getDayOfWeek().getValue();
        if (!Objects.equals(timeSlot.getWeekday(), weekday)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "课次日期的星期与时间段星期不一致");
        }
        Set<Long> teacherIds = distinctTeacherIds(request.teacherIds());
        if (teacherIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "至少需要一名授课教师");
        }
        for (Long teacherId : teacherIds) {
            TeacherEntity teacher = teacherMapper.selectById(teacherId);
            if (teacher == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "教师不存在：" + teacherId);
            }
            if (!Objects.equals(teacher.getInstitutionId(), request.institutionId())
                || !Objects.equals(teacher.getCampusId(), request.campusId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "教师的机构/校区与课次不一致");
            }
        }
        validateLessonLinkage(request, teachingClass, currentLessonId);
        return new ScheduleMaterial(weekday, timeSlot.getStartTime(), timeSlot.getEndTime());
    }

    private void validateLessonLinkage(ScheduleLessonRequest request, TeachingClassEntity teachingClass, Long currentLessonId) {
        String lessonStatus = defaultStatus(request.lessonStatus());
        String changeType = normalizeChangeType(request.changeType());
        Long relatedLessonId = request.relatedLessonId();

        if (!MAKEUP.equalsIgnoreCase(lessonStatus)) {
            if (changeType != null || relatedLessonId != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "只有补课课次才允许填写变更类型和关联的原课次");
            }
            return;
        }

        if (changeType == null && relatedLessonId == null) {
            return;
        }

        if (!Objects.equals(changeType, CANCELLED_MAKEUP) || relatedLessonId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "补课关联信息不完整，需要同时提供停课补课的变更类型和关联的原课次");
        }
        if (currentLessonId != null && Objects.equals(currentLessonId, relatedLessonId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "关联的原课次不能是当前课次本身");
        }

        ScheduleLessonEntity relatedLesson = requireLesson(relatedLessonId);
        if (!Objects.equals(relatedLesson.getInstitutionId(), request.institutionId())
            || !Objects.equals(relatedLesson.getCampusId(), request.campusId())
            || !Objects.equals(relatedLesson.getTermId(), request.termId())
            || !Objects.equals(relatedLesson.getClassId(), teachingClass.getId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "关联原课次不属于同一班级范围");
        }
        if (!CANCELLED.equalsIgnoreCase(relatedLesson.getLessonStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "关联的原课次必须是已停课课次，才能安排补课");
        }
    }

    private void assertNoConflicts(ScheduleLessonRequest request, Long excludeLessonId) {
        if (!occupiesSchedulingSlot(defaultStatus(request.lessonStatus()))) {
            return;
        }
        ScheduleConflictCheckResponse response = detectConflicts(request, excludeLessonId);
        if (Boolean.TRUE.equals(response.conflict())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, String.join("; ", response.messages()));
        }
    }

    private ScheduleConflictCheckResponse detectConflicts(ScheduleLessonRequest request, Long excludeLessonId) {
        List<String> types = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        Long classCount = lessonMapper.selectCount(conflictWrapper(excludeLessonId)
            .eq(ScheduleLessonEntity::getClassId, request.classId())
            .eq(ScheduleLessonEntity::getLessonDate, request.lessonDate())
            .eq(ScheduleLessonEntity::getTimeSlotId, request.timeSlotId()));
        if (classCount != null && classCount > 0) {
            types.add("class");
            messages.add("该班级在此时段已有排课");
        }
        Long classroomCount = lessonMapper.selectCount(conflictWrapper(excludeLessonId)
            .eq(ScheduleLessonEntity::getClassroomId, request.classroomId())
            .eq(ScheduleLessonEntity::getLessonDate, request.lessonDate())
            .eq(ScheduleLessonEntity::getTimeSlotId, request.timeSlotId()));
        if (classroomCount != null && classroomCount > 0) {
            types.add("classroom");
            messages.add("该教室在此时段已被占用");
        }
        for (Long teacherId : distinctTeacherIds(request.teacherIds())) {
            Long teacherCount = teacherRelMapper.countTeacherConflicts(
                teacherId,
                request.lessonDate(),
                request.timeSlotId(),
                excludeLessonId
            );
            if (teacherCount != null && teacherCount > 0) {
                types.add("teacher");
                messages.add("该教师在此时段已被占用：" + teacherId);
            }
        }
        return new ScheduleConflictCheckResponse(!types.isEmpty(), types, messages);
    }

    private LambdaQueryWrapper<ScheduleLessonEntity> conflictWrapper(Long excludeLessonId) {
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = new LambdaQueryWrapper<ScheduleLessonEntity>()
            .in(ScheduleLessonEntity::getLessonStatus, SCHEDULED, RESCHEDULED, MAKEUP);
        if (excludeLessonId != null) {
            wrapper.ne(ScheduleLessonEntity::getId, excludeLessonId);
        }
        return wrapper;
    }

    private ScheduleLessonEntity toEntity(ScheduleLessonRequest request, ScheduleMaterial material) {
        ScheduleLessonEntity entity = new ScheduleLessonEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setTermId(request.termId());
        entity.setClassId(request.classId());
        entity.setClassroomId(request.classroomId());
        entity.setTimeSlotId(request.timeSlotId());
        entity.setLessonDate(request.lessonDate());
        entity.setLessonWeekday(material.weekday());
        entity.setStartTime(material.startTime());
        entity.setEndTime(material.endTime());
        entity.setLessonTitle(normalize(request.lessonTitle()));
        entity.setLessonStatus(defaultStatus(request.lessonStatus()));
        entity.setChangeType(normalizeChangeType(request.changeType()));
        entity.setRelatedLessonId(request.relatedLessonId());
        entity.setRemark(request.remark());
        return entity;
    }

    private void replaceTeachers(Long lessonId, List<Long> teacherIds) {
        teacherRelMapper.physicalDeleteByLessonId(lessonId);
        for (Long teacherId : distinctTeacherIds(teacherIds)) {
            ScheduleTeacherRelEntity relation = new ScheduleTeacherRelEntity();
            relation.setLessonId(lessonId);
            relation.setTeacherId(teacherId);
            relation.setDeleted(Boolean.FALSE);
            teacherRelMapper.insert(relation);
        }
    }

    private List<ScheduleLessonView> toViews(List<ScheduleLessonEntity> entities, Boolean pendingMakeupOnly) {
        Set<Long> cancelledLessonIds = entities.stream()
            .filter(entity -> CANCELLED.equalsIgnoreCase(entity.getLessonStatus()))
            .map(ScheduleLessonEntity::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Long> linkedLessonIds = cancelledLessonIds.isEmpty()
            ? List.of()
            : lessonMapper.selectLinkedCancelledLessonIds(List.copyOf(cancelledLessonIds));
        Set<Long> linkedCancelledLessonIds = linkedLessonIds == null
            ? Set.of()
            : new HashSet<>(linkedLessonIds);

        return entities.stream()
            .map(entity -> toView(entity, linkedCancelledLessonIds))
            .filter(view -> !Boolean.TRUE.equals(pendingMakeupOnly) || Boolean.TRUE.equals(view.pendingMakeup()))
            .toList();
    }

    private ScheduleLessonView toView(ScheduleLessonEntity entity, Set<Long> linkedCancelledLessonIds) {
        List<Long> teacherIds = teacherRelMapper.selectList(new LambdaQueryWrapper<ScheduleTeacherRelEntity>()
                .eq(ScheduleTeacherRelEntity::getLessonId, entity.getId()))
            .stream()
            .map(ScheduleTeacherRelEntity::getTeacherId)
            .toList();
        boolean pendingMakeup = CANCELLED.equalsIgnoreCase(entity.getLessonStatus())
            && !linkedCancelledLessonIds.contains(entity.getId());
        return new ScheduleLessonView(
            entity.getId(),
            entity.getInstitutionId(),
            entity.getCampusId(),
            entity.getTermId(),
            entity.getClassId(),
            entity.getClassroomId(),
            entity.getTimeSlotId(),
            entity.getLessonDate(),
            entity.getLessonWeekday(),
            entity.getStartTime(),
            entity.getEndTime(),
            entity.getLessonTitle(),
            entity.getLessonStatus(),
            entity.getChangeType(),
            entity.getRelatedLessonId(),
            pendingMakeup,
            teacherIds,
            entity.getRemark()
        );
    }

    private ScheduleLessonEntity requireLesson(Long id) {
        ScheduleLessonEntity entity = lessonMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "排课课次不存在：" + id);
        }
        return entity;
    }

    private LambdaQueryWrapper<ScheduleLessonEntity> baseOrder() {
        return new LambdaQueryWrapper<ScheduleLessonEntity>()
            .orderByAsc(ScheduleLessonEntity::getLessonDate)
            .orderByAsc(ScheduleLessonEntity::getStartTime);
    }

    private Set<Long> distinctTeacherIds(List<Long> teacherIds) {
        return new LinkedHashSet<>(teacherIds == null ? List.of() : teacherIds);
    }

    private String defaultStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null || normalized.isBlank()) {
            return SCHEDULED;
        }
        String canonical = normalized.toLowerCase();
        if (!List.of(SCHEDULED, RESCHEDULED, CANCELLED, MAKEUP, COMPLETED).contains(canonical)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "课次状态只能是已排课、已调课、已停课、已补课、已结课之一");
        }
        return canonical;
    }

    private String normalizeChangeType(String changeType) {
        String normalized = normalize(changeType);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String canonical = normalized.toLowerCase();
        if (!CANCELLED_MAKEUP.equals(canonical)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "变更类型只能为停课补课");
        }
        return canonical;
    }

    private boolean occupiesSchedulingSlot(String lessonStatus) {
        return SCHEDULED.equalsIgnoreCase(lessonStatus)
            || RESCHEDULED.equalsIgnoreCase(lessonStatus)
            || MAKEUP.equalsIgnoreCase(lessonStatus);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private record ScheduleMaterial(Integer weekday, LocalTime startTime, LocalTime endTime) {
    }
}
