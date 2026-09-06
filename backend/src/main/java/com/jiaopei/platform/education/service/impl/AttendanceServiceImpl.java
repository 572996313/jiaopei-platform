package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.AttendanceEntity;
import com.jiaopei.platform.education.domain.entity.ClassStudentEntity;
import com.jiaopei.platform.education.domain.entity.ScheduleLessonEntity;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.AttendanceBatchItemRequest;
import com.jiaopei.platform.education.dto.AttendanceCreateRequest;
import com.jiaopei.platform.education.dto.AttendanceLessonBatchSaveRequest;
import com.jiaopei.platform.education.dto.AttendanceLessonInfoView;
import com.jiaopei.platform.education.dto.AttendanceLessonPageView;
import com.jiaopei.platform.education.dto.AttendanceLessonStudentView;
import com.jiaopei.platform.education.dto.AttendanceLessonSummaryView;
import com.jiaopei.platform.education.mapper.AttendanceMapper;
import com.jiaopei.platform.education.mapper.ClassStudentMapper;
import com.jiaopei.platform.education.mapper.ScheduleLessonMapper;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.AttendanceService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceServiceImpl implements AttendanceService {

    private static final String LESSON_STATUS_SCHEDULED = "scheduled";
    private static final String ATTENDANCE_STATUS_PRESENT = "present";
    private static final String ATTENDANCE_STATUS_ABSENT = "absent";
    private static final String ATTENDANCE_STATUS_LEAVE = "leave";
    private static final String ATTENDANCE_STATUS_LATE = "late";
    private static final Set<String> ALLOWED_ATTENDANCE_STATUSES = Set.of(
        ATTENDANCE_STATUS_PRESENT,
        ATTENDANCE_STATUS_ABSENT,
        ATTENDANCE_STATUS_LEAVE,
        ATTENDANCE_STATUS_LATE
    );

    private final AttendanceMapper attendanceMapper;
    private final ClassStudentMapper classStudentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final StudentMapper studentMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;

    public AttendanceServiceImpl(AttendanceMapper attendanceMapper, ClassStudentMapper classStudentMapper,
                                 TeachingClassMapper teachingClassMapper, StudentMapper studentMapper,
                                 ScheduleLessonMapper scheduleLessonMapper) {
        this.attendanceMapper = attendanceMapper;
        this.classStudentMapper = classStudentMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.studentMapper = studentMapper;
        this.scheduleLessonMapper = scheduleLessonMapper;
    }

    @Override
    public List<AttendanceEntity> listAttendances(Long lessonId, Long classId, Long studentId, LocalDate attendanceDate) {
        LambdaQueryWrapper<AttendanceEntity> wrapper = new LambdaQueryWrapper<AttendanceEntity>()
            .orderByDesc(AttendanceEntity::getAttendanceDate)
            .orderByAsc(AttendanceEntity::getClassId)
            .orderByAsc(AttendanceEntity::getStudentId);
        if (lessonId != null) {
            wrapper.eq(AttendanceEntity::getLessonId, lessonId);
        }
        if (classId != null) {
            wrapper.eq(AttendanceEntity::getClassId, classId);
        }
        if (studentId != null) {
            wrapper.eq(AttendanceEntity::getStudentId, studentId);
        }
        if (attendanceDate != null) {
            wrapper.eq(AttendanceEntity::getAttendanceDate, attendanceDate);
        }
        return attendanceMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public AttendanceEntity createAttendance(AttendanceCreateRequest request) {
        TeachingClassEntity teachingClass = requireClass(request.classId());
        StudentEntity student = requireStudent(request.studentId());
        ScheduleLessonEntity lesson = request.lessonId() == null ? null : requireScheduledLesson(request.lessonId());
        Long institutionId = request.institutionId() == null ? teachingClass.getInstitutionId() : request.institutionId();
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId) || !Objects.equals(student.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级与学员必须属于同一机构：" + institutionId);
        }
        if (lesson != null) {
            assertLessonMatchesAttendance(lesson, request.classId(), institutionId, request.attendanceDate());
        }
        requireActiveClassStudent(request.classId(), request.studentId(), request.attendanceDate());
        assertUniqueAttendance(request.lessonId(), request.classId(), request.studentId(), request.attendanceDate());

        AttendanceEntity entity = new AttendanceEntity();
        entity.setInstitutionId(institutionId);
        entity.setClassId(request.classId());
        entity.setLessonId(request.lessonId());
        entity.setStudentId(request.studentId());
        entity.setAttendanceDate(request.attendanceDate());
        entity.setAttendanceStatus(normalizeAttendanceStatus(request.attendanceStatus(), ATTENDANCE_STATUS_PRESENT));
        entity.setSource(defaultSource(request.source()));
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            attendanceMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw duplicateAttendanceException(request.lessonId());
        }
        return entity;
    }

    @Override
    public AttendanceLessonPageView getLessonAttendancePage(Long lessonId) {
        ScheduleLessonEntity lesson = requireScheduledLesson(lessonId);
        return buildLessonAttendancePage(lesson);
    }

    @Override
    @Transactional
    public AttendanceLessonPageView saveLessonAttendance(AttendanceLessonBatchSaveRequest request) {
        ScheduleLessonEntity lesson = requireScheduledLesson(request.lessonId());
        List<ClassStudentEntity> classStudents = listActiveClassStudents(lesson.getClassId(), lesson.getLessonDate());
        Map<Long, ClassStudentEntity> classStudentMap = classStudents.stream()
            .collect(Collectors.toMap(ClassStudentEntity::getStudentId, item -> item, (left, right) -> right, LinkedHashMap::new));

        Map<Long, AttendanceBatchItemRequest> requestItemMap = new LinkedHashMap<>();
        for (AttendanceBatchItemRequest item : request.items()) {
            requestItemMap.put(item.studentId(), item);
        }
        Map<Long, AttendanceEntity> existingAttendanceMap = listLessonAttendances(lesson.getId()).stream()
            .collect(Collectors.toMap(AttendanceEntity::getStudentId, item -> item, (left, right) -> right, LinkedHashMap::new));

        for (AttendanceBatchItemRequest item : requestItemMap.values()) {
            if (isBlank(item.attendanceStatus())) {
                continue;
            }
            ClassStudentEntity classStudent = classStudentMap.get(item.studentId());
            if (classStudent == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该学员不是课次班级的在班学员：" + item.studentId());
            }
            AttendanceEntity existing = existingAttendanceMap.get(item.studentId());
            if (existing == null) {
                AttendanceEntity entity = new AttendanceEntity();
                entity.setInstitutionId(lesson.getInstitutionId());
                entity.setClassId(lesson.getClassId());
                entity.setLessonId(lesson.getId());
                entity.setStudentId(item.studentId());
                entity.setAttendanceDate(lesson.getLessonDate());
                entity.setAttendanceStatus(normalizeAttendanceStatus(item.attendanceStatus(), ATTENDANCE_STATUS_PRESENT));
                entity.setSource(defaultSource(item.source()));
                entity.setRemark(item.remark());
                entity.setDeleted(Boolean.FALSE);
                try {
                    attendanceMapper.insert(entity);
                } catch (DuplicateKeyException exception) {
                    throw duplicateAttendanceException(lesson.getId());
                }
                existingAttendanceMap.put(item.studentId(), entity);
                continue;
            }
            existing.setAttendanceStatus(normalizeAttendanceStatus(item.attendanceStatus(), existing.getAttendanceStatus()));
            existing.setSource(defaultSource(item.source()));
            existing.setRemark(item.remark());
            existing.setInstitutionId(lesson.getInstitutionId());
            existing.setClassId(lesson.getClassId());
            existing.setLessonId(lesson.getId());
            existing.setAttendanceDate(lesson.getLessonDate());
            attendanceMapper.updateById(existing);
        }
        return buildLessonAttendancePage(lesson);
    }

    private AttendanceLessonPageView buildLessonAttendancePage(ScheduleLessonEntity lesson) {
        TeachingClassEntity teachingClass = requireClass(lesson.getClassId());
        List<ClassStudentEntity> classStudents = listActiveClassStudents(lesson.getClassId(), lesson.getLessonDate());
        List<AttendanceEntity> existingAttendances = listLessonAttendances(lesson.getId());
        Map<Long, AttendanceEntity> attendanceMap = existingAttendances.stream()
            .collect(Collectors.toMap(AttendanceEntity::getStudentId, item -> item, (left, right) -> right, LinkedHashMap::new));
        Map<Long, StudentEntity> studentMap = loadStudentMap(classStudents);

        List<AttendanceLessonStudentView> students = new ArrayList<>();
        int markedCount = 0;
        int presentCount = 0;
        int absentCount = 0;
        int leaveCount = 0;
        int lateCount = 0;
        for (ClassStudentEntity classStudent : classStudents) {
            StudentEntity student = studentMap.get(classStudent.getStudentId());
            AttendanceEntity attendance = attendanceMap.get(classStudent.getStudentId());
            String status = attendance == null ? null : attendance.getAttendanceStatus();
            if (!isBlank(status)) {
                markedCount++;
                switch (status) {
                    case ATTENDANCE_STATUS_PRESENT -> presentCount++;
                    case ATTENDANCE_STATUS_ABSENT -> absentCount++;
                    case ATTENDANCE_STATUS_LEAVE -> leaveCount++;
                    case ATTENDANCE_STATUS_LATE -> lateCount++;
                    default -> {
                    }
                }
            }
            students.add(new AttendanceLessonStudentView(
                classStudent.getStudentId(),
                student == null ? null : student.getStudentNo(),
                student == null ? null : student.getStudentName(),
                attendance == null ? null : attendance.getId(),
                status,
                attendance == null ? null : attendance.getSource(),
                attendance == null ? null : attendance.getRemark()
            ));
        }

        String message = students.isEmpty() ? "该班级没有在班学员" : null;
        return new AttendanceLessonPageView(
            new AttendanceLessonInfoView(
                lesson.getId(),
                lesson.getInstitutionId(),
                lesson.getCampusId(),
                lesson.getClassId(),
                teachingClass.getClassName(),
                lesson.getLessonDate(),
                lesson.getStartTime(),
                lesson.getEndTime(),
                lesson.getLessonTitle(),
                lesson.getLessonStatus(),
                lesson.getRemark()
            ),
            students,
            new AttendanceLessonSummaryView(
                students.size(),
                markedCount,
                presentCount,
                absentCount,
                leaveCount,
                lateCount
            ),
            message
        );
    }

    private Map<Long, StudentEntity> loadStudentMap(List<ClassStudentEntity> classStudents) {
        if (classStudents.isEmpty()) {
            return Map.of();
        }
        List<Long> studentIds = classStudents.stream()
            .map(ClassStudentEntity::getStudentId)
            .toList();
        return studentMapper.selectBatchIds(studentIds).stream()
            .map(StudentEntity.class::cast)
            .collect(Collectors.toMap(StudentEntity::getId, item -> item, (left, right) -> right, HashMap::new));
    }

    private List<ClassStudentEntity> listActiveClassStudents(Long classId, LocalDate lessonDate) {
        LambdaQueryWrapper<ClassStudentEntity> wrapper = new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStatus, "active")
            .and(q -> q.isNull(ClassStudentEntity::getJoinedAt).or().le(ClassStudentEntity::getJoinedAt, lessonDate))
            .and(q -> q.isNull(ClassStudentEntity::getLeftAt).or().ge(ClassStudentEntity::getLeftAt, lessonDate))
            .orderByAsc(ClassStudentEntity::getStudentId);
        return classStudentMapper.selectList(wrapper);
    }

    private List<AttendanceEntity> listLessonAttendances(Long lessonId) {
        return attendanceMapper.selectList(new LambdaQueryWrapper<AttendanceEntity>()
            .eq(AttendanceEntity::getLessonId, lessonId)
            .orderByAsc(AttendanceEntity::getStudentId));
    }

    private ScheduleLessonEntity requireScheduledLesson(Long lessonId) {
        ScheduleLessonEntity lesson = scheduleLessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "排课课次不存在：" + lessonId);
        }
        if (!LESSON_STATUS_SCHEDULED.equals(lesson.getLessonStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已排课的课次才能点名");
        }
        return lesson;
    }

    private TeachingClassEntity requireClass(Long classId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + classId);
        }
        return teachingClass;
    }

    private StudentEntity requireStudent(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学员不存在：" + studentId);
        }
        return student;
    }

    private void requireActiveClassStudent(Long classId, Long studentId, LocalDate attendanceDate) {
        LambdaQueryWrapper<ClassStudentEntity> wrapper = new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStudentId, studentId)
            .eq(ClassStudentEntity::getStatus, "active");
        if (attendanceDate != null) {
            wrapper.and(q -> q.isNull(ClassStudentEntity::getJoinedAt).or().le(ClassStudentEntity::getJoinedAt, attendanceDate))
                .and(q -> q.isNull(ClassStudentEntity::getLeftAt).or().ge(ClassStudentEntity::getLeftAt, attendanceDate));
        }
        Long count = classStudentMapper.selectCount(wrapper);
        if (count == null || count == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该学员不是此班级的在班学员：" + classId);
        }
    }

    private void assertUniqueAttendance(Long lessonId, Long classId, Long studentId, LocalDate attendanceDate) {
        LambdaQueryWrapper<AttendanceEntity> wrapper = new LambdaQueryWrapper<AttendanceEntity>()
            .eq(AttendanceEntity::getStudentId, studentId);
        if (lessonId != null) {
            wrapper.eq(AttendanceEntity::getLessonId, lessonId);
        } else {
            wrapper.eq(AttendanceEntity::getClassId, classId)
                .eq(AttendanceEntity::getAttendanceDate, attendanceDate);
        }
        Long count = attendanceMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw duplicateAttendanceException(lessonId);
        }
    }

    private void assertLessonMatchesAttendance(ScheduleLessonEntity lesson, Long classId, Long institutionId, LocalDate attendanceDate) {
        if (!Objects.equals(lesson.getClassId(), classId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "考勤班级与课次班级不一致");
        }
        if (!Objects.equals(lesson.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "考勤机构与课次机构不一致");
        }
        if (attendanceDate != null && !Objects.equals(lesson.getLessonDate(), attendanceDate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "考勤日期与课次日期不一致");
        }
    }

    private String normalizeAttendanceStatus(String rawStatus, String defaultValue) {
        String candidate = isBlank(rawStatus) ? defaultValue : rawStatus.trim().toLowerCase(Locale.ROOT);
        if (isBlank(candidate) || !ALLOWED_ATTENDANCE_STATUSES.contains(candidate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的考勤状态：" + rawStatus);
        }
        return candidate;
    }

    private String defaultSource(String source) {
        return isBlank(source) ? "manual" : source.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException duplicateAttendanceException(Long lessonId) {
        if (lessonId != null) {
            return new BusinessException(ResultCode.BAD_REQUEST, "该学员在此课次已有考勤记录");
        }
        return new BusinessException(ResultCode.BAD_REQUEST, "该学员在当天已存在考勤记录");
    }
}
