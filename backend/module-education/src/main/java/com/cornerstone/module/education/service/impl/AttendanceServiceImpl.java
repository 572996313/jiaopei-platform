package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.AttendanceBatchItemRequest;
import com.cornerstone.module.education.dto.AttendanceCreateRequest;
import com.cornerstone.module.education.dto.AttendanceLessonBatchSaveRequest;
import com.cornerstone.module.education.dto.AttendanceLessonInfoView;
import com.cornerstone.module.education.dto.AttendanceLessonPageView;
import com.cornerstone.module.education.dto.AttendanceLessonProgressView;
import com.cornerstone.module.education.dto.AttendanceLessonStudentView;
import com.cornerstone.module.education.dto.AttendanceLessonSummaryView;
import com.cornerstone.module.education.mapper.AttendanceMapper;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.AttendanceService;
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
    private static final String LESSON_STATUS_RESCHEDULED = "rescheduled";
    private static final String LESSON_STATUS_MAKEUP = "makeup";
    private static final String LESSON_STATUS_CANCELLED = "cancelled";
    private static final String LESSON_STATUS_COMPLETED = "completed";
    // 调课/补课课次仍要实际上课，已结课允许回查补录点名，只有停课课次不产生考勤
    private static final Set<String> ATTENDANCE_ALLOWED_LESSON_STATUSES = Set.of(
        LESSON_STATUS_SCHEDULED,
        LESSON_STATUS_RESCHEDULED,
        LESSON_STATUS_MAKEUP,
        LESSON_STATUS_COMPLETED
    );
    private static final String ATTENDANCE_STATUS_PRESENT = "present";
    private static final String ATTENDANCE_STATUS_ABSENT = "absent";
    private static final String ATTENDANCE_STATUS_LEAVE = "leave";
    private static final String ATTENDANCE_STATUS_LATE = "late";
    // 用 List 而不是 Set：报错文案要把可选值按点名界面的顺序原样列给操作员，Set 的迭代顺序不固定
    private static final List<String> ALLOWED_ATTENDANCE_STATUSES = List.of(
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
    public List<AttendanceLessonProgressView> listLessonProgress(List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return List.of();
        }
        List<ScheduleLessonEntity> lessons = scheduleLessonMapper.selectBatchIds(lessonIds);
        if (lessons.isEmpty()) {
            return List.of();
        }
        List<Long> classIds = lessons.stream()
            .map(ScheduleLessonEntity::getClassId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, List<ClassStudentEntity>> rosterByClass = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudentEntity>()
                    .in(!classIds.isEmpty(), ClassStudentEntity::getClassId, classIds)
                    .eq(ClassStudentEntity::getStatus, "active"))
            .stream()
            .collect(Collectors.groupingBy(ClassStudentEntity::getClassId));
        Map<Long, List<AttendanceEntity>> attendanceByLesson = attendanceMapper.selectList(
                new LambdaQueryWrapper<AttendanceEntity>()
                    .in(AttendanceEntity::getLessonId, lessonIds))
            .stream()
            .filter(item -> item.getLessonId() != null)
            .collect(Collectors.groupingBy(AttendanceEntity::getLessonId));

        List<AttendanceLessonProgressView> progressList = new ArrayList<>();
        for (ScheduleLessonEntity lesson : lessons) {
            if (!ATTENDANCE_ALLOWED_LESSON_STATUSES.contains(lesson.getLessonStatus())) {
                continue;
            }
            List<ClassStudentEntity> roster = rosterByClass.getOrDefault(lesson.getClassId(), List.of()).stream()
                .filter(item -> item.getJoinedAt() == null || !item.getJoinedAt().isAfter(lesson.getLessonDate()))
                .filter(item -> item.getLeftAt() == null || !item.getLeftAt().isBefore(lesson.getLessonDate()))
                .toList();
            Map<Long, AttendanceEntity> attendanceByStudent = attendanceByLesson
                .getOrDefault(lesson.getId(), List.of()).stream()
                .collect(Collectors.toMap(AttendanceEntity::getStudentId, item -> item, (left, right) -> right));
            int markedCount = 0;
            int presentCount = 0;
            int absentCount = 0;
            int leaveCount = 0;
            int lateCount = 0;
            for (ClassStudentEntity classStudent : roster) {
                AttendanceEntity attendance = attendanceByStudent.get(classStudent.getStudentId());
                String status = attendance == null ? null : attendance.getAttendanceStatus();
                if (isBlank(status)) {
                    continue;
                }
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
            progressList.add(new AttendanceLessonProgressView(
                lesson.getId(), roster.size(), markedCount, presentCount, absentCount, leaveCount, lateCount));
        }
        return progressList;
    }

    @Override
    @Transactional
    public AttendanceEntity createAttendance(AttendanceCreateRequest request) {
        TeachingClassEntity teachingClass = requireClass(request.classId());
        StudentEntity student = requireStudent(request.studentId());
        ScheduleLessonEntity lesson = request.lessonId() == null ? null : requireAttendanceAllowedLesson(request.lessonId());
        Long institutionId = request.institutionId() == null ? teachingClass.getInstitutionId() : request.institutionId();
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId) || !Objects.equals(student.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级与学员必须属于同一机构");
        }
        if (lesson != null) {
            assertLessonMatchesAttendance(lesson, request.classId(), institutionId, request.attendanceDate());
        }
        requireActiveClassStudent(teachingClass, student, request.attendanceDate());
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
        ScheduleLessonEntity lesson = requireAttendanceAllowedLesson(lessonId);
        return buildLessonAttendancePage(lesson);
    }

    @Override
    @Transactional
    public AttendanceLessonPageView saveLessonAttendance(AttendanceLessonBatchSaveRequest request) {
        ScheduleLessonEntity lesson = requireAttendanceAllowedLesson(request.lessonId());
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
            ClassStudentEntity classStudent = classStudentMap.get(item.studentId());
            if (isBlank(item.attendanceStatus())) {
                // 留空 = 撤销这次点名：原来记过就删掉，不然操作员清空再保存会"改了没反应"（旧实现是直接跳过）
                AttendanceEntity revoked = existingAttendanceMap.remove(item.studentId());
                if (revoked != null) {
                    attendanceMapper.physicalDeleteById(revoked.getId());
                }
                continue;
            }
            if (classStudent == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "学员「" + studentLabel(item.studentId()) + "」不是本课次班级的在班学员");
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

    private ScheduleLessonEntity requireAttendanceAllowedLesson(Long lessonId) {
        ScheduleLessonEntity lesson = scheduleLessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "排课课次不存在：" + lessonId);
        }
        if (!ATTENDANCE_ALLOWED_LESSON_STATUSES.contains(lesson.getLessonStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已停课的课次不能点名，请先安排补课或恢复排课");
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

    private void requireActiveClassStudent(TeachingClassEntity teachingClass, StudentEntity student,
                                           LocalDate attendanceDate) {
        LambdaQueryWrapper<ClassStudentEntity> wrapper = new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, teachingClass.getId())
            .eq(ClassStudentEntity::getStudentId, student.getId())
            .eq(ClassStudentEntity::getStatus, "active");
        if (attendanceDate != null) {
            wrapper.and(q -> q.isNull(ClassStudentEntity::getJoinedAt).or().le(ClassStudentEntity::getJoinedAt, attendanceDate))
                .and(q -> q.isNull(ClassStudentEntity::getLeftAt).or().ge(ClassStudentEntity::getLeftAt, attendanceDate));
        }
        Long count = classStudentMapper.selectCount(wrapper);
        if (count == null || count == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "学员「" + studentDisplayName(student) + "」不是班级「" + classDisplayName(teachingClass) + "」的在班学员");
        }
    }

    /** 报错文案给操作员看，19 位雪花 ID 认不出来：先姓名，其次编号，都取不到才退回 ID。 */
    private String studentLabel(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        return student == null ? "#" + studentId : studentDisplayName(student);
    }

    private String studentDisplayName(StudentEntity student) {
        return firstNotBlank(student.getStudentName(), student.getStudentNo(), "#" + student.getId());
    }

    private String classDisplayName(TeachingClassEntity teachingClass) {
        return firstNotBlank(teachingClass.getClassName(), teachingClass.getClassCode(), "#" + teachingClass.getId());
    }

    private String firstNotBlank(String... candidates) {
        for (String candidate : candidates) {
            if (!isBlank(candidate)) {
                return candidate;
            }
        }
        return "";
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
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "不支持的考勤状态：" + rawStatus + "，可选值：" + String.join("、", ALLOWED_ATTENDANCE_STATUSES));
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
