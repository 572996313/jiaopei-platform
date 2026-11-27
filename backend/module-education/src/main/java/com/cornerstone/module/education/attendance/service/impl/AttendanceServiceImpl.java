package com.cornerstone.module.education.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.attendance.dto.AttendanceBatchItemRequest;
import com.cornerstone.module.education.attendance.dto.AttendanceCreateRequest;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonBatchSaveRequest;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonInfoView;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonPageView;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonProgressView;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonStudentView;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonSummaryView;
import com.cornerstone.module.education.attendance.dto.AttendanceMonthSubtotalView;
import com.cornerstone.module.education.attendance.dto.AttendanceStudentDetailResponse;
import com.cornerstone.module.education.attendance.dto.AttendanceStudentLessonView;
import com.cornerstone.module.education.attendance.dto.AttendanceSummaryQuery;
import com.cornerstone.module.education.attendance.dto.AttendanceSummaryResponse;
import com.cornerstone.module.education.attendance.dto.AttendanceSummaryRowView;
import com.cornerstone.module.education.attendance.dto.AttendanceSummaryTotalsView;
import com.cornerstone.module.education.attendance.service.AttendanceService;
import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.AttendanceMapper;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.support.EducationCampusScopeSupport;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
    // 持考勤管理码可代点任意课次；只有课次点名码的账号限本人授课课次。
    // 权限码只回答"能不能点名"，"能点谁的课"必须由课次教师关系兜住，否则一个教师账号能点全校的名。
    private static final String ATTENDANCE_MANAGE_PERMISSION = "education:attendance:manage";
    /** 汇总区间上限:一个季度 92 天足够日常盘点,放宽到一年防呆 */
    private static final int ATTENDANCE_SUMMARY_MAX_RANGE_DAYS = 366;
    private static final String SUMMARY_GROUP_BY_CLASS = "class";
    private static final String SUMMARY_GROUP_BY_STUDENT = "student";

    private final AttendanceMapper attendanceMapper;
    private final ClassStudentMapper classStudentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final StudentMapper studentMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final ScheduleTeacherRelMapper scheduleTeacherRelMapper;
    private final TeacherMapper teacherMapper;

    public AttendanceServiceImpl(AttendanceMapper attendanceMapper, ClassStudentMapper classStudentMapper,
                                 TeachingClassMapper teachingClassMapper, StudentMapper studentMapper,
                                 ScheduleLessonMapper scheduleLessonMapper,
                                 ScheduleTeacherRelMapper scheduleTeacherRelMapper,
                                 TeacherMapper teacherMapper) {
        this.attendanceMapper = attendanceMapper;
        this.classStudentMapper = classStudentMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.studentMapper = studentMapper;
        this.scheduleLessonMapper = scheduleLessonMapper;
        this.scheduleTeacherRelMapper = scheduleTeacherRelMapper;
        this.teacherMapper = teacherMapper;
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
        EducationCampusScopeSupport.applyClassVisible(wrapper, AttendanceEntity::getClassId);
        return attendanceMapper.selectList(wrapper);
    }

    @Override
    public List<AttendanceLessonProgressView> listLessonProgress(List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return List.of();
        }
        List<ScheduleLessonEntity> lessons = scheduleLessonMapper.selectBatchIds(lessonIds).stream()
            // 调用方可能传入未按范围收窄的课次 id，这里再兜一层，避免回显到其他校区
            .filter(lesson -> CampusScopeGuard.canAccess(lesson.getCampusId()))
            .toList();
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
        CampusScopeGuard.assertCampus(teachingClass.getCampusId(), "考勤");
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
        CampusScopeGuard.assertCampus(lesson.getCampusId(), "考勤");
        return buildLessonAttendancePage(lesson);
    }

    @Override
    @Transactional
    public AttendanceLessonPageView saveLessonAttendance(AttendanceLessonBatchSaveRequest request) {
        ScheduleLessonEntity lesson = requireAttendanceAllowedLesson(request.lessonId());
        CampusScopeGuard.assertCampus(lesson.getCampusId(), "考勤");
        assertLessonMarkableByCurrentUser(lesson);
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

    // ===================== 考勤汇总(按班级/按学员)与学员考勤明细 =====================

    /** 单节课的点名快照:应到在班学员 + 该课次已有考勤行,按班级/按学员两种聚合都从它出发 */
    private record LessonAttendanceSnapshot(ScheduleLessonEntity lesson, Set<Long> rosterStudentIds,
                                            Map<Long, AttendanceEntity> attendanceByStudent) {
    }

    /** 单节课的点名计数:expected=在班人数,marked=已点到人数 */
    private record LessonCounters(int expected, int marked, int present, int late, int leave, int absent) {
    }

    /** 可累加的计数桶:班级/月份/学员/区间合计共用同一套字段,聚合完一次性转只读视图 */
    private static final class CountBucket {
        private int lessonCount;
        private int fullyMarkedLessons;
        private int pendingLessons;
        private int expectedCount;
        private int presentCount;
        private int lateCount;
        private int leaveCount;
        private int absentCount;

        /** 课次维度累加:应到>0 且全部点到记已点名,应到>0 但没点满记未点名(与点名页口径一致) */
        private void addLesson(int expected, int marked, int present, int late, int leave, int absent) {
            this.lessonCount++;
            this.expectedCount += expected;
            this.presentCount += present;
            this.lateCount += late;
            this.leaveCount += leave;
            this.absentCount += absent;
            if (expected > 0) {
                if (marked >= expected) {
                    this.fullyMarkedLessons++;
                } else {
                    this.pendingLessons++;
                }
            }
        }

        /** 学员维度累加:应到课次 = 当天在班 ∪ 有考勤行;没有考勤行即未点名 */
        private void addStudentLesson(boolean hasRow, String status) {
            this.lessonCount++;
            this.expectedCount++;
            if (hasRow) {
                this.fullyMarkedLessons++;
                switch (status == null ? "" : status) {
                    case ATTENDANCE_STATUS_PRESENT -> this.presentCount++;
                    case ATTENDANCE_STATUS_LATE -> this.lateCount++;
                    case ATTENDANCE_STATUS_LEAVE -> this.leaveCount++;
                    case ATTENDANCE_STATUS_ABSENT -> this.absentCount++;
                    default -> {
                    }
                }
            } else {
                this.pendingLessons++;
            }
        }

        private Integer attendanceRate() {
            return expectedCount == 0 ? null : Math.round(presentCount * 100.0f / expectedCount);
        }

        private AttendanceSummaryTotalsView toTotals() {
            return new AttendanceSummaryTotalsView(lessonCount, fullyMarkedLessons, pendingLessons,
                expectedCount, presentCount, lateCount, leaveCount, absentCount);
        }
    }

    private static final AttendanceSummaryTotalsView EMPTY_TOTALS =
        new AttendanceSummaryTotalsView(0, 0, 0, 0, 0, 0, 0, 0);

    @Override
    public AttendanceSummaryResponse getSummary(Long institutionId, AttendanceSummaryQuery query) {
        LocalDate dateFrom = query.getDateFrom();
        LocalDate dateTo = query.getDateTo();
        assertSummaryRange(dateFrom, dateTo);
        String groupBy = normalizeSummaryGroupBy(query.getGroupBy());

        List<LessonAttendanceSnapshot> snapshots = buildLessonSnapshots(
            listAttendanceRangeLessons(institutionId, dateFrom, dateTo, query.getCampusId(), query.getClassId()));

        // 区间合计是课次级口径,与分组维度无关
        CountBucket totals = new CountBucket();
        for (LessonAttendanceSnapshot snapshot : snapshots) {
            LessonCounters counters = countLesson(snapshot);
            totals.addLesson(counters.expected(), counters.marked(), counters.present(),
                counters.late(), counters.leave(), counters.absent());
        }

        List<AttendanceSummaryRowView> rows = SUMMARY_GROUP_BY_STUDENT.equals(groupBy)
            ? buildStudentSummaryRows(snapshots, query.getStudentId())
            : buildClassSummaryRows(snapshots);
        return new AttendanceSummaryResponse(dateFrom, dateTo, groupBy, totals.toTotals(), rows);
    }

    @Override
    public AttendanceStudentDetailResponse getStudentDetail(Long institutionId, Long studentId,
                                                            LocalDate dateFrom, LocalDate dateTo) {
        assertSummaryRange(dateFrom, dateTo);
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null || !Objects.equals(student.getInstitutionId(), institutionId)) {
            // 跨机构与不存在同样报"不存在",不泄露其他机构有没有这个学员
            throw new BusinessException(ResultCode.NOT_FOUND, "学员不存在：" + studentId);
        }

        // 该学员的全部归属关系(预占不算):含已退班/结业,靠 joined_at/left_at 的日期过滤覆盖退班前的课次
        List<ClassStudentEntity> memberships = classStudentMapper.selectList(
            new LambdaQueryWrapper<ClassStudentEntity>()
                .eq(ClassStudentEntity::getStudentId, studentId)
                .ne(ClassStudentEntity::getStatus, "reserved"));
        List<Long> classIds = memberships.stream()
            .map(ClassStudentEntity::getClassId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (classIds.isEmpty()) {
            return new AttendanceStudentDetailResponse(studentId, student.getStudentNo(), student.getStudentName(),
                dateFrom, dateTo, EMPTY_TOTALS, List.of());
        }
        if (CampusScopeGuard.restricted()) {
            // 与学员列表同口径：没有任何班级落在可访问校区的学员，对外表现为"不存在"
            LambdaQueryWrapper<TeachingClassEntity> visibleClasses = new LambdaQueryWrapper<TeachingClassEntity>()
                .in(TeachingClassEntity::getId, classIds);
            CampusScopeGuard.apply(visibleClasses, TeachingClassEntity::getCampusId);
            Long visible = teachingClassMapper.selectCount(visibleClasses);
            if (visible == null || visible == 0) {
                throw new BusinessException(ResultCode.NOT_FOUND, "学员不存在：" + studentId);
            }
        }

        List<ScheduleLessonEntity> lessons = listStudentRangeLessons(institutionId, classIds, dateFrom, dateTo);
        if (lessons.isEmpty()) {
            return new AttendanceStudentDetailResponse(studentId, student.getStudentNo(), student.getStudentName(),
                dateFrom, dateTo, EMPTY_TOTALS, List.of());
        }

        Map<Long, String> classNames = loadClassNames(classIds);
        Map<Long, AttendanceEntity> attendanceByLesson = attendanceMapper.selectList(
                new LambdaQueryWrapper<AttendanceEntity>()
                    .in(AttendanceEntity::getLessonId, lessons.stream().map(ScheduleLessonEntity::getId).toList())
                    .eq(AttendanceEntity::getStudentId, studentId))
            .stream()
            .filter(item -> item.getLessonId() != null)
            .collect(Collectors.toMap(AttendanceEntity::getLessonId, item -> item, (left, right) -> right));

        List<AttendanceStudentLessonView> lessonViews = new ArrayList<>();
        int presentCount = 0;
        int lateCount = 0;
        int leaveCount = 0;
        int absentCount = 0;
        for (ScheduleLessonEntity lesson : lessons) {
            AttendanceEntity row = attendanceByLesson.get(lesson.getId());
            boolean inClass = isStudentInClassOnDate(memberships, lesson.getClassId(), lesson.getLessonDate());
            if (!inClass && row == null) {
                // 既不在班也没有点名记录的课次与这位学员无关(例如入班前的课次)
                continue;
            }
            String status = row == null ? null : row.getAttendanceStatus();
            if (status != null) {
                switch (status) {
                    case ATTENDANCE_STATUS_PRESENT -> presentCount++;
                    case ATTENDANCE_STATUS_LATE -> lateCount++;
                    case ATTENDANCE_STATUS_LEAVE -> leaveCount++;
                    case ATTENDANCE_STATUS_ABSENT -> absentCount++;
                    default -> {
                    }
                }
            }
            lessonViews.add(new AttendanceStudentLessonView(
                lesson.getId(),
                lesson.getClassId(),
                classNames.get(lesson.getClassId()),
                lesson.getLessonDate(),
                lesson.getStartTime(),
                lesson.getEndTime(),
                lesson.getLessonStatus(),
                lesson.getLessonTitle(),
                status,
                row == null ? null : row.getSource(),
                row == null ? null : row.getRemark()
            ));
        }

        int expectedTotal = lessonViews.size();
        int markedTotal = (int) lessonViews.stream().filter(view -> view.attendanceStatus() != null).count();
        AttendanceSummaryTotalsView summary = new AttendanceSummaryTotalsView(
            expectedTotal, markedTotal, expectedTotal - markedTotal,
            expectedTotal, presentCount, lateCount, leaveCount, absentCount);
        return new AttendanceStudentDetailResponse(studentId, student.getStudentNo(), student.getStudentName(),
            dateFrom, dateTo, summary, lessonViews);
    }

    private List<ScheduleLessonEntity> listAttendanceRangeLessons(Long institutionId, LocalDate dateFrom,
                                                                  LocalDate dateTo, Long campusId, Long classId) {
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = new LambdaQueryWrapper<ScheduleLessonEntity>()
            .eq(ScheduleLessonEntity::getInstitutionId, institutionId)
            .ge(ScheduleLessonEntity::getLessonDate, dateFrom)
            .le(ScheduleLessonEntity::getLessonDate, dateTo)
            .eq(campusId != null, ScheduleLessonEntity::getCampusId, campusId)
            .eq(classId != null, ScheduleLessonEntity::getClassId, classId)
            .in(ScheduleLessonEntity::getLessonStatus, ATTENDANCE_ALLOWED_LESSON_STATUSES)
            .orderByAsc(ScheduleLessonEntity::getLessonDate)
            .orderByAsc(ScheduleLessonEntity::getStartTime);
        if (campusId != null) {
            CampusScopeGuard.assertCampus(campusId, "考勤汇总");
        }
        CampusScopeGuard.apply(wrapper, ScheduleLessonEntity::getCampusId);
        return scheduleLessonMapper.selectList(wrapper);
    }

    private List<ScheduleLessonEntity> listStudentRangeLessons(Long institutionId, List<Long> classIds,
                                                               LocalDate dateFrom, LocalDate dateTo) {
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = new LambdaQueryWrapper<ScheduleLessonEntity>()
            .eq(ScheduleLessonEntity::getInstitutionId, institutionId)
            .in(ScheduleLessonEntity::getClassId, classIds)
            .ge(ScheduleLessonEntity::getLessonDate, dateFrom)
            .le(ScheduleLessonEntity::getLessonDate, dateTo)
            .in(ScheduleLessonEntity::getLessonStatus, ATTENDANCE_ALLOWED_LESSON_STATUSES)
            .orderByAsc(ScheduleLessonEntity::getLessonDate)
            .orderByAsc(ScheduleLessonEntity::getStartTime);
        // 学员的全部归属关系可能横跨校区，明细只能取可访问校区内的课次
        CampusScopeGuard.apply(wrapper, ScheduleLessonEntity::getCampusId);
        return scheduleLessonMapper.selectList(wrapper);
    }

    /** 批量构建课次点名快照:一次取齐名册与考勤行,内存里按课次日期过滤在班学员(口径同 listLessonProgress) */
    private List<LessonAttendanceSnapshot> buildLessonSnapshots(List<ScheduleLessonEntity> lessons) {
        if (lessons.isEmpty()) {
            return List.of();
        }
        List<Long> classIds = lessons.stream()
            .map(ScheduleLessonEntity::getClassId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, List<ClassStudentEntity>> rosterByClass = classIds.isEmpty() ? Map.of()
            : classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudentEntity>()
                    .in(ClassStudentEntity::getClassId, classIds)
                    .eq(ClassStudentEntity::getStatus, "active"))
                .stream()
                .collect(Collectors.groupingBy(ClassStudentEntity::getClassId));
        Map<Long, List<AttendanceEntity>> attendanceByLesson = attendanceMapper.selectList(
                new LambdaQueryWrapper<AttendanceEntity>()
                    .in(AttendanceEntity::getLessonId, lessons.stream().map(ScheduleLessonEntity::getId).toList()))
            .stream()
            .filter(item -> item.getLessonId() != null)
            .collect(Collectors.groupingBy(AttendanceEntity::getLessonId));

        List<LessonAttendanceSnapshot> snapshots = new ArrayList<>();
        for (ScheduleLessonEntity lesson : lessons) {
            if (lesson.getClassId() == null) {
                // 没挂班级的课次无法归属,防御性跳过
                continue;
            }
            Set<Long> rosterStudentIds = rosterByClass.getOrDefault(lesson.getClassId(), List.of()).stream()
                .filter(item -> item.getJoinedAt() == null || !item.getJoinedAt().isAfter(lesson.getLessonDate()))
                .filter(item -> item.getLeftAt() == null || !item.getLeftAt().isBefore(lesson.getLessonDate()))
                .map(ClassStudentEntity::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<Long, AttendanceEntity> byStudent = attendanceByLesson
                .getOrDefault(lesson.getId(), List.of()).stream()
                .filter(item -> item.getStudentId() != null)
                .collect(Collectors.toMap(AttendanceEntity::getStudentId, item -> item, (left, right) -> right));
            snapshots.add(new LessonAttendanceSnapshot(lesson, rosterStudentIds, byStudent));
        }
        return snapshots;
    }

    private LessonCounters countLesson(LessonAttendanceSnapshot snapshot) {
        int marked = 0;
        int present = 0;
        int late = 0;
        int leave = 0;
        int absent = 0;
        for (Long studentId : snapshot.rosterStudentIds()) {
            AttendanceEntity attendance = snapshot.attendanceByStudent().get(studentId);
            String status = attendance == null ? null : attendance.getAttendanceStatus();
            if (isBlank(status)) {
                continue;
            }
            marked++;
            switch (status) {
                case ATTENDANCE_STATUS_PRESENT -> present++;
                case ATTENDANCE_STATUS_LATE -> late++;
                case ATTENDANCE_STATUS_LEAVE -> leave++;
                case ATTENDANCE_STATUS_ABSENT -> absent++;
                default -> {
                }
            }
        }
        return new LessonCounters(snapshot.rosterStudentIds().size(), marked, present, late, leave, absent);
    }

    /** 按班级聚合:每班一行 + 按月小计(季档展开行用,不再额外拉课次明细),班级名内存填充,前端不用回查 */
    private List<AttendanceSummaryRowView> buildClassSummaryRows(List<LessonAttendanceSnapshot> snapshots) {
        Map<Long, CountBucket> buckets = new LinkedHashMap<>();
        Map<Long, Map<String, CountBucket>> monthBuckets = new LinkedHashMap<>();
        for (LessonAttendanceSnapshot snapshot : snapshots) {
            LessonCounters counters = countLesson(snapshot);
            Long classId = snapshot.lesson().getClassId();
            buckets.computeIfAbsent(classId, key -> new CountBucket())
                .addLesson(counters.expected(), counters.marked(), counters.present(),
                    counters.late(), counters.leave(), counters.absent());
            monthBuckets.computeIfAbsent(classId, key -> new TreeMap<>())
                .computeIfAbsent(YearMonth.from(snapshot.lesson().getLessonDate()).toString(), key -> new CountBucket())
                .addLesson(counters.expected(), counters.marked(), counters.present(),
                    counters.late(), counters.leave(), counters.absent());
        }
        Map<Long, String> classNames = loadClassNames(buckets.keySet());
        return buckets.entrySet().stream()
            .map(entry -> {
                CountBucket bucket = entry.getValue();
                return new AttendanceSummaryRowView(
                    entry.getKey(),
                    classNames.get(entry.getKey()),
                    null, null, null, null,
                    bucket.lessonCount,
                    bucket.fullyMarkedLessons,
                    bucket.pendingLessons,
                    bucket.expectedCount,
                    bucket.presentCount,
                    bucket.lateCount,
                    bucket.leaveCount,
                    bucket.absentCount,
                    bucket.attendanceRate(),
                    monthBuckets.getOrDefault(entry.getKey(), Map.of()).entrySet().stream()
                        .map(monthEntry -> new AttendanceMonthSubtotalView(
                            monthEntry.getKey(),
                            monthEntry.getValue().lessonCount,
                            monthEntry.getValue().fullyMarkedLessons,
                            monthEntry.getValue().pendingLessons,
                            monthEntry.getValue().expectedCount,
                            monthEntry.getValue().presentCount,
                            monthEntry.getValue().lateCount,
                            monthEntry.getValue().leaveCount,
                            monthEntry.getValue().absentCount,
                            monthEntry.getValue().attendanceRate()))
                        .toList());
            })
            .sorted(Comparator.comparing(AttendanceSummaryRowView::className,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /** 按学员聚合:应到 = 当天在班 ∪ 有考勤行,已退班学员退班前的点名记录不会算成"出勤 > 应到" */
    private List<AttendanceSummaryRowView> buildStudentSummaryRows(List<LessonAttendanceSnapshot> snapshots,
                                                                   Long studentIdFilter) {
        Map<Long, CountBucket> buckets = new LinkedHashMap<>();
        Map<Long, Set<Long>> classIdsByStudent = new LinkedHashMap<>();
        for (LessonAttendanceSnapshot snapshot : snapshots) {
            Set<Long> involvedStudentIds = new LinkedHashSet<>(snapshot.rosterStudentIds());
            involvedStudentIds.addAll(snapshot.attendanceByStudent().keySet());
            if (studentIdFilter != null) {
                involvedStudentIds.retainAll(Set.of(studentIdFilter));
            }
            if (involvedStudentIds.isEmpty()) {
                continue;
            }
            Long classId = snapshot.lesson().getClassId();
            for (Long studentId : involvedStudentIds) {
                AttendanceEntity row = snapshot.attendanceByStudent().get(studentId);
                String status = row == null ? null : row.getAttendanceStatus();
                buckets.computeIfAbsent(studentId, key -> new CountBucket()).addStudentLesson(row != null, status);
                classIdsByStudent.computeIfAbsent(studentId, key -> new LinkedHashSet<>()).add(classId);
            }
        }
        if (buckets.isEmpty()) {
            return List.of();
        }
        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(buckets.keySet()).stream()
            .map(StudentEntity.class::cast)
            .collect(Collectors.toMap(StudentEntity::getId, item -> item, (left, right) -> right));
        Set<Long> involvedClassIds = classIdsByStudent.values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
        Map<Long, String> classNames = loadClassNames(involvedClassIds);
        return buckets.entrySet().stream()
            .map(entry -> {
                StudentEntity student = studentMap.get(entry.getKey());
                List<String> studentClassNames = classIdsByStudent.getOrDefault(entry.getKey(), Set.of()).stream()
                    .map(classNames::get)
                    .filter(Objects::nonNull)
                    .toList();
                CountBucket bucket = entry.getValue();
                return new AttendanceSummaryRowView(
                    null, null,
                    entry.getKey(),
                    student == null ? null : student.getStudentNo(),
                    student == null ? null : student.getStudentName(),
                    studentClassNames,
                    bucket.lessonCount,
                    bucket.fullyMarkedLessons,
                    bucket.pendingLessons,
                    bucket.expectedCount,
                    bucket.presentCount,
                    bucket.lateCount,
                    bucket.leaveCount,
                    bucket.absentCount,
                    bucket.attendanceRate(),
                    List.of());
            })
            // 默认出勤率升序:最缺勤的学员排前面,这正是运营要先把目光落在谁身上的问题
            .sorted(Comparator.comparing(AttendanceSummaryRowView::attendanceRate,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AttendanceSummaryRowView::studentId, Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();
    }

    private Map<Long, String> loadClassNames(Collection<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return Map.of();
        }
        return teachingClassMapper.selectBatchIds(classIds).stream()
            .collect(Collectors.toMap(TeachingClassEntity::getId,
                cls -> firstNotBlank(cls.getClassName(), cls.getClassCode()), (left, right) -> left));
    }

    private boolean isStudentInClassOnDate(List<ClassStudentEntity> memberships, Long classId, LocalDate lessonDate) {
        for (ClassStudentEntity membership : memberships) {
            if (!Objects.equals(membership.getClassId(), classId)) {
                continue;
            }
            boolean joined = membership.getJoinedAt() == null || !membership.getJoinedAt().isAfter(lessonDate);
            boolean notLeft = membership.getLeftAt() == null || !membership.getLeftAt().isBefore(lessonDate);
            if (joined && notLeft) {
                return true;
            }
        }
        return false;
    }

    private void assertSummaryRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "统计区间不能为空");
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "统计区间不合法：起始日期不能晚于截止日期");
        }
        if (dateFrom.plusDays(ATTENDANCE_SUMMARY_MAX_RANGE_DAYS).isBefore(dateTo)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "统计区间最长 " + ATTENDANCE_SUMMARY_MAX_RANGE_DAYS + " 天");
        }
    }

    private String normalizeSummaryGroupBy(String rawGroupBy) {
        String candidate = isBlank(rawGroupBy) ? SUMMARY_GROUP_BY_CLASS : rawGroupBy.trim().toLowerCase(Locale.ROOT);
        if (!SUMMARY_GROUP_BY_CLASS.equals(candidate) && !SUMMARY_GROUP_BY_STUDENT.equals(candidate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "不支持的分组维度：" + rawGroupBy + "，可选值：class、student");
        }
        return candidate;
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

    private void assertLessonMarkableByCurrentUser(ScheduleLessonEntity lesson) {
        if (AuthContext.hasPermission(ATTENDANCE_MANAGE_PERMISSION)) {
            return;
        }
        Long userId = AuthContext.requireUser().userId();
        TeacherEntity teacher = teacherMapper.selectOne(new LambdaQueryWrapper<TeacherEntity>()
            .eq(TeacherEntity::getUserId, userId)
            .eq(TeacherEntity::getInstitutionId, lesson.getInstitutionId()));
        if (teacher == null) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                "当前账号未绑定本机构的教师档案，无法点名；请先绑定教师档案或授予考勤管理权限");
        }
        Long markedCount = scheduleTeacherRelMapper.selectCount(new LambdaQueryWrapper<ScheduleTeacherRelEntity>()
            .eq(ScheduleTeacherRelEntity::getLessonId, lesson.getId())
            .eq(ScheduleTeacherRelEntity::getTeacherId, teacher.getId()));
        if (markedCount == null || markedCount == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                "只能为本人授课的课次点名，本课次的授课教师里没有「" + teacher.getTeacherName() + "」");
        }
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
