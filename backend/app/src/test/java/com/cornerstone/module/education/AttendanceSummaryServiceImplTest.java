package com.cornerstone.module.education;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.AttendanceMonthSubtotalView;
import com.cornerstone.module.education.dto.AttendanceStudentDetailResponse;
import com.cornerstone.module.education.dto.AttendanceSummaryQuery;
import com.cornerstone.module.education.dto.AttendanceSummaryResponse;
import com.cornerstone.module.education.dto.AttendanceSummaryRowView;
import com.cornerstone.module.education.mapper.AttendanceMapper;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.service.impl.AttendanceServiceImpl;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 考勤汇总（按班级/按学员）与学员考勤明细的统计口径：
 * 在班判定按 joined_at/left_at 与课次日期比较；学员维度的应到 = 当天在班 ∪ 有考勤行。
 */
class AttendanceSummaryServiceImplTest {

    private static final Long INSTITUTION_ID = 100L;
    private static final Long CLASS_ID = 800L;
    private static final Long OTHER_CLASS_ID = 801L;
    private static final Long STUDENT_ID = 5L;

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AttendanceEntity.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudentEntity.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleLessonEntity.class);
    }

    private final AttendanceMapper attendanceMapper = mock(AttendanceMapper.class);
    private final ClassStudentMapper classStudentMapper = mock(ClassStudentMapper.class);
    private final TeachingClassMapper teachingClassMapper = mock(TeachingClassMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final ScheduleLessonMapper scheduleLessonMapper = mock(ScheduleLessonMapper.class);
    private final AttendanceServiceImpl service = new AttendanceServiceImpl(
        attendanceMapper, classStudentMapper, teachingClassMapper, studentMapper, scheduleLessonMapper,
        mock(ScheduleTeacherRelMapper.class), mock(TeacherMapper.class));

    @Test
    void classSummaryAppliesRosterDateFilterAndMonthSubtotals() {
        // 两节课分属两个月；第三节课没有在班学员（应到 0），第四节课属另一个班
        when(scheduleLessonMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
            lesson(1L, CLASS_ID, LocalDate.of(2026, 10, 3)),
            lesson(2L, CLASS_ID, LocalDate.of(2026, 11, 5)),
            lesson(3L, CLASS_ID, LocalDate.of(2026, 10, 10)),
            lesson(4L, OTHER_CLASS_ID, LocalDate.of(2026, 10, 7)))));

        // 学员 1 在 10-04 离班（11-05 的课不算他），学员 2 在 10-11 才入班（10 月两节课都不算他），
        // 学员 3 在 10-04 离班（11-05 的课不算他）；于是 10-10 那节课没有任何在班学员
        when(classStudentMapper.selectList(any())).thenReturn(List.of(
            classStudent(CLASS_ID, 1L, null, LocalDate.of(2026, 10, 4)),
            classStudent(CLASS_ID, 2L, LocalDate.of(2026, 10, 11), null),
            classStudent(CLASS_ID, 3L, null, LocalDate.of(2026, 10, 4)),
            classStudent(OTHER_CLASS_ID, 4L, null, null)));
        when(attendanceMapper.selectList(any())).thenReturn(List.of(
            attendanceRow(11L, 1L, 1L, "present"),
            // 课次 2 的这条记录属于已离班的学员 3：课次维度只统计在班名单，不计入
            attendanceRow(12L, 2L, 3L, "absent"),
            attendanceRow(13L, 4L, 4L, "present")));
        when(teachingClassMapper.selectBatchIds(any())).thenReturn(List.of(
            teachingClass(CLASS_ID, "A班"),
            teachingClass(OTHER_CLASS_ID, "B班")));

        AttendanceSummaryResponse response = service.getSummary(INSTITUTION_ID,
            query(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), "class"));

        assertEquals(4, response.summary().lessonCount());
        assertEquals(4, response.summary().expectedCount());
        assertEquals(2, response.summary().presentCount());
        assertEquals(0, response.summary().absentCount());
        assertEquals(1, response.summary().fullyMarkedLessons());
        assertEquals(2, response.summary().pendingLessons());

        assertEquals(2, response.rows().size());
        AttendanceSummaryRowView classRow = response.rows().get(0);
        assertEquals(CLASS_ID, classRow.classId());
        assertEquals("A班", classRow.className());
        assertEquals(3, classRow.lessonCount());
        assertEquals(0, classRow.fullyMarkedLessons());
        assertEquals(2, classRow.pendingLessons());
        // 应到人次 = 10-03 的两个在班(学员 1、3) + 11-05 的一个在班(学员 2)；空名册课次不贡献人次
        assertEquals(3, classRow.expectedCount());
        assertEquals(1, classRow.presentCount());
        assertEquals(33, classRow.attendanceRate());

        // 按月小计：10 月两节课（含空名册那节），11 月一节课
        List<AttendanceMonthSubtotalView> months = classRow.months();
        assertEquals(2, months.size());
        assertEquals("2026-10", months.get(0).month());
        assertEquals(2, months.get(0).lessonCount());
        assertEquals(2, months.get(0).expectedCount());
        assertEquals(1, months.get(0).presentCount());
        assertEquals(50, months.get(0).attendanceRate());
        assertEquals("2026-11", months.get(1).month());
        assertEquals(1, months.get(1).lessonCount());
        assertEquals(0, months.get(1).attendanceRate());

        AttendanceSummaryRowView otherClassRow = response.rows().get(1);
        assertEquals(OTHER_CLASS_ID, otherClassRow.classId());
        assertEquals("B班", otherClassRow.className());
        assertEquals(100, otherClassRow.attendanceRate());
    }

    @Test
    void emptyRosterLessonCountsAsNeitherMarkedNorPending() {
        when(scheduleLessonMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
            lesson(1L, CLASS_ID, LocalDate.of(2026, 10, 3)))));
        when(classStudentMapper.selectList(any())).thenReturn(List.of());
        when(attendanceMapper.selectList(any())).thenReturn(List.of());
        when(teachingClassMapper.selectBatchIds(any())).thenReturn(List.of(teachingClass(CLASS_ID, "A班")));

        AttendanceSummaryResponse response = service.getSummary(INSTITUTION_ID,
            query(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "class"));

        assertEquals(1, response.summary().lessonCount());
        assertEquals(0, response.summary().fullyMarkedLessons());
        assertEquals(0, response.summary().pendingLessons());
        assertEquals(0, response.rows().get(0).expectedCount());
        assertNull(response.rows().get(0).attendanceRate());
    }

    @Test
    void studentSummaryUsesUnionOfRosterAndAttendanceRows() {
        ScheduleLessonEntity firstLesson = lesson(1L, CLASS_ID, LocalDate.of(2026, 10, 3));
        ScheduleLessonEntity secondLesson = lesson(2L, CLASS_ID, LocalDate.of(2026, 10, 5));
        when(scheduleLessonMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(firstLesson, secondLesson)));
        when(classStudentMapper.selectList(any())).thenReturn(List.of(
            classStudent(CLASS_ID, 1L, null, null),
            classStudent(CLASS_ID, 2L, null, null)));
        // 学员 3 已退班（不在名册）但留有点名记录；学员 1 只到了第一节；第二节整节未点名
        when(attendanceMapper.selectList(any())).thenReturn(List.of(
            attendanceRow(11L, 1L, 1L, "present"),
            attendanceRow(12L, 1L, 3L, "absent")));
        when(teachingClassMapper.selectBatchIds(any())).thenReturn(List.of(teachingClass(CLASS_ID, "书法班")));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(
            student(1L, "S001", "张三"),
            student(2L, "S002", "李四"),
            student(3L, "S003", "王五")));

        AttendanceSummaryResponse response = service.getSummary(INSTITUTION_ID,
            query(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "student"));

        assertEquals(3, response.rows().size());
        // 出勤率升序：两个 0% 在前（同率按学员 id 升序），50% 在后
        AttendanceSummaryRowView lowest = response.rows().get(0);
        assertEquals(2L, lowest.studentId());
        assertEquals("李四", lowest.studentName());
        assertEquals(2, lowest.lessonCount());
        assertEquals(0, lowest.fullyMarkedLessons());
        assertEquals(2, lowest.pendingLessons());
        assertEquals(0, lowest.attendanceRate());
        assertEquals(List.of("书法班"), lowest.classNames());

        AttendanceSummaryRowView quitStudent = response.rows().get(1);
        assertEquals(3L, quitStudent.studentId());
        // 退班学员凭考勤行进入统计：应到 1、缺勤 1，出勤率 0%
        assertEquals(1, quitStudent.lessonCount());
        assertEquals(1, quitStudent.absentCount());
        assertEquals(0, quitStudent.attendanceRate());

        AttendanceSummaryRowView presentStudent = response.rows().get(2);
        assertEquals(1L, presentStudent.studentId());
        assertEquals("S001", presentStudent.studentNo());
        assertEquals(2, presentStudent.lessonCount());
        assertEquals(1, presentStudent.presentCount());
        assertEquals(50, presentStudent.attendanceRate());
    }

    @Test
    void studentSummarySupportsSingleStudentFilter() {
        when(scheduleLessonMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
            lesson(1L, CLASS_ID, LocalDate.of(2026, 10, 3)))));
        when(classStudentMapper.selectList(any())).thenReturn(List.of(
            classStudent(CLASS_ID, 1L, null, null),
            classStudent(CLASS_ID, 2L, null, null)));
        when(attendanceMapper.selectList(any())).thenReturn(List.of(
            attendanceRow(11L, 1L, 1L, "late")));
        when(teachingClassMapper.selectBatchIds(any())).thenReturn(List.of(teachingClass(CLASS_ID, "书法班")));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(student(2L, "S002", "李四")));

        AttendanceSummaryQuery query = query(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "student");
        query.setStudentId(2L);
        AttendanceSummaryResponse response = service.getSummary(INSTITUTION_ID, query);

        assertEquals(1, response.rows().size());
        assertEquals(2L, response.rows().get(0).studentId());
        // 区间合计仍是课次级口径，不随学员筛选收窄
        assertEquals(1, response.summary().lessonCount());
        assertEquals(2, response.summary().expectedCount());
        assertEquals(1, response.summary().pendingLessons());
    }

    @Test
    void studentDetailMarksUnmarkedLessonsAsPending() {
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student(STUDENT_ID, "S005", "赵六"));
        when(classStudentMapper.selectList(any())).thenReturn(List.of(
            classStudent(CLASS_ID, STUDENT_ID, LocalDate.of(2026, 10, 1), null)));
        when(scheduleLessonMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
            lesson(1L, CLASS_ID, LocalDate.of(2026, 10, 3)),
            lesson(2L, CLASS_ID, LocalDate.of(2026, 10, 5)))));
        when(attendanceMapper.selectList(any())).thenReturn(List.of(
            attendanceRow(11L, 1L, STUDENT_ID, "present")));
        when(teachingClassMapper.selectBatchIds(any())).thenReturn(List.of(teachingClass(CLASS_ID, "书法班")));

        AttendanceStudentDetailResponse response = service.getStudentDetail(INSTITUTION_ID, STUDENT_ID,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals("赵六", response.studentName());
        assertEquals(2, response.summary().lessonCount());
        assertEquals(1, response.summary().fullyMarkedLessons());
        assertEquals(1, response.summary().pendingLessons());
        assertEquals(2, response.lessons().size());
        assertEquals("present", response.lessons().get(0).attendanceStatus());
        assertEquals("书法班", response.lessons().get(0).className());
        // 没有考勤行的课次 = 未点名，状态返回 null 由前端渲染
        assertNull(response.lessons().get(1).attendanceStatus());
    }

    @Test
    void studentDetailIgnoresLessonsOutsideMembershipDates() {
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student(STUDENT_ID, "S005", "赵六"));
        // 10-04 才入班：10-03 的课既不在班也无考勤行，不应出现在明细里
        when(classStudentMapper.selectList(any())).thenReturn(List.of(
            classStudent(CLASS_ID, STUDENT_ID, LocalDate.of(2026, 10, 4), null)));
        when(scheduleLessonMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
            lesson(1L, CLASS_ID, LocalDate.of(2026, 10, 3)),
            lesson(2L, CLASS_ID, LocalDate.of(2026, 10, 5)))));
        when(attendanceMapper.selectList(any())).thenReturn(List.of());
        when(teachingClassMapper.selectBatchIds(any())).thenReturn(List.of(teachingClass(CLASS_ID, "书法班")));

        AttendanceStudentDetailResponse response = service.getStudentDetail(INSTITUTION_ID, STUDENT_ID,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals(1, response.lessons().size());
        assertEquals(2L, response.lessons().get(0).lessonId());
    }

    @Test
    void studentDetailRejectsStudentOfOtherInstitution() {
        StudentEntity otherInstitutionStudent = student(STUDENT_ID, "S005", "赵六");
        otherInstitutionStudent.setInstitutionId(999L);
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(otherInstitutionStudent);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.getStudentDetail(INSTITUTION_ID, STUDENT_ID,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertEquals(ResultCode.NOT_FOUND, exception.getResultCode());
    }

    @Test
    void summaryRejectsOversizedRangeAndUnknownGroupBy() {
        BusinessException rangeException = assertThrows(BusinessException.class,
            () -> service.getSummary(INSTITUTION_ID,
                query(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1), "class")));
        assertEquals(ResultCode.BAD_REQUEST, rangeException.getResultCode());

        BusinessException groupByException = assertThrows(BusinessException.class,
            () -> service.getSummary(INSTITUTION_ID,
                query(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "school")));
        assertEquals(ResultCode.BAD_REQUEST, groupByException.getResultCode());
        assertEquals("不支持的分组维度：school，可选值：class、student", groupByException.getMessage());
    }

    private AttendanceSummaryQuery query(LocalDate dateFrom, LocalDate dateTo, String groupBy) {
        AttendanceSummaryQuery query = new AttendanceSummaryQuery();
        query.setDateFrom(dateFrom);
        query.setDateTo(dateTo);
        query.setGroupBy(groupBy);
        return query;
    }

    private ScheduleLessonEntity lesson(Long id, Long classId, LocalDate lessonDate) {
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setId(id);
        lesson.setInstitutionId(INSTITUTION_ID);
        lesson.setClassId(classId);
        lesson.setLessonDate(lessonDate);
        lesson.setLessonStatus("scheduled");
        return lesson;
    }

    private ClassStudentEntity classStudent(Long classId, Long studentId, LocalDate joinedAt, LocalDate leftAt) {
        ClassStudentEntity classStudent = new ClassStudentEntity();
        classStudent.setInstitutionId(INSTITUTION_ID);
        classStudent.setClassId(classId);
        classStudent.setStudentId(studentId);
        classStudent.setStatus("active");
        classStudent.setJoinedAt(joinedAt);
        classStudent.setLeftAt(leftAt);
        return classStudent;
    }

    private AttendanceEntity attendanceRow(Long id, Long lessonId, Long studentId, String status) {
        AttendanceEntity entity = new AttendanceEntity();
        entity.setId(id);
        entity.setInstitutionId(INSTITUTION_ID);
        entity.setClassId(CLASS_ID);
        entity.setLessonId(lessonId);
        entity.setStudentId(studentId);
        entity.setAttendanceDate(LocalDate.of(2026, 10, 3));
        entity.setAttendanceStatus(status);
        entity.setSource("manual");
        return entity;
    }

    private TeachingClassEntity teachingClass(Long id, String className) {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(id);
        teachingClass.setInstitutionId(INSTITUTION_ID);
        teachingClass.setClassName(className);
        return teachingClass;
    }

    private StudentEntity student(Long id, String studentNo, String studentName) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setInstitutionId(INSTITUTION_ID);
        student.setStudentNo(studentNo);
        student.setStudentName(studentName);
        return student;
    }
}
