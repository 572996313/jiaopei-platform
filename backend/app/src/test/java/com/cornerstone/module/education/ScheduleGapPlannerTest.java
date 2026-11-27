package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.SchedulePlanEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.SchedulePlanMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.schedule.dto.ScheduleAutoPlacement;
import com.cornerstone.module.education.schedule.dto.ScheduleAutoStrategy;
import com.cornerstone.module.education.schedule.dto.ScheduleCompletenessResponse;
import com.cornerstone.module.education.schedule.dto.ScheduleSlotCell;
import com.cornerstone.module.education.schedule.service.SchoolCalendarService;
import com.cornerstone.module.education.schedule.support.ScheduleGapPlanner;
import com.cornerstone.module.education.schedule.support.ScheduleRuleSupport;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 缺口补排的决策口径：占用按教师分桶、停排日只作兜底档、教师周上限与每日上限是硬约束、
 * 教师/教室口径不一致的班整班退回而不猜。
 */
class ScheduleGapPlannerTest {

    private static final long INSTITUTION = 1L;
    private static final long CAMPUS = 10L;
    private static final long CLASS_A = 100L;
    private static final long CLASS_B = 101L;
    private static final long ROOM_A = 200L;
    private static final long ROOM_B = 201L;
    private static final long TEACHER_A = 300L;
    private static final long TEACHER_B = 301L;
    private static final long SLOT_MON_1 = 1001L;
    private static final long SLOT_MON_2 = 1002L;
    private static final long SLOT_WED_1 = 1003L;
    private static final long OCCUPYING_LESSON_ID = 900L;

    /** 引擎不往今天之前排课，所以用例的周必须落在未来；按"两周后的周一"推，避免日期写死后过期。 */
    private static final LocalDate MONDAY = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(2);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    private final SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
    private final ScheduleLessonMapper lessonMapper = mock(ScheduleLessonMapper.class);
    private final ScheduleTeacherRelMapper teacherRelMapper = mock(ScheduleTeacherRelMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
    private final TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
    private final SchoolCalendarService calendarService = mock(SchoolCalendarService.class);
    private final ScheduleRuleSupport ruleSupport = mock(ScheduleRuleSupport.class);
    private final ScheduleGapPlanner planner = new ScheduleGapPlanner(teacherMapper, classroomMapper,
        timeSlotMapper, planMapper, lessonMapper, teacherRelMapper, ruleSupport, calendarService,
        new ObjectMapper());

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SchedulePlanEntity.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleLessonEntity.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleTeacherRelEntity.class);
        TableInfoHelper.initTableInfo(assistant, TeachingClassEntity.class);
        TableInfoHelper.initTableInfo(assistant, TimeSlotEntity.class);
    }

    @Test
    void anotherTeachersOccupiedCellDoesNotBlockThisClass() {
        // B 班老师周一第一节有课，不该让 A 班补不进同一格——占用集合必须按教师分桶
        stubWorld(List.of(slot(SLOT_MON_1, 1, "上午1", LocalTime.of(8, 0))), List.of(plan(CLASS_A, ROOM_A,
            "[300]")), List.of(teacher(TEACHER_A, "张老师", null, null)), List.of(classroom(ROOM_A)),
            List.of(occupiedLesson()), List.of(relation(OCCUPYING_LESSON_ID, TEACHER_B)));
        ScheduleCompletenessResponse report = report(gapRow(CLASS_A, "A班", 1,
            new ScheduleCompletenessResponse.Gap(MONDAY, 1, SLOT_MON_1, "上午1")));

        ScheduleGapPlanner.Result result = planner.plan(INSTITUTION, CAMPUS, MONDAY, SUNDAY, List.of(),
            null, report);

        assertEquals(1, result.placements().size());
        ScheduleAutoPlacement placement = result.placements().get(0);
        assertEquals(MONDAY, placement.lessonDate());
        assertEquals(SLOT_MON_1, placement.timeSlotId());
        assertEquals("plan_gap", placement.source());
        assertNull(placement.notice());
    }

    @Test
    void closedDayIsUsedOnlyWhenNothingElseIsFree() {
        stubWorld(List.of(slot(SLOT_MON_1, 1, "上午1", LocalTime.of(8, 0)), slot(SLOT_WED_1, 3, "上午1",
            LocalTime.of(8, 0))), List.of(plan(CLASS_A, ROOM_A, "[300]")),
            List.of(teacher(TEACHER_A, "张老师", null, null)), List.of(classroom(ROOM_A)), List.of(), List.of());
        when(calendarService.closedDays(INSTITUTION, CAMPUS, MONDAY, SUNDAY))
            .thenReturn(Map.of(MONDAY, "国庆节（法定假日）"));
        ScheduleCompletenessResponse report = report(targetRow(CLASS_A, "A班", 2));

        ScheduleGapPlanner.Result result = planner.plan(INSTITUTION, CAMPUS, MONDAY, SUNDAY, List.of(),
            null, report);

        assertEquals(2, result.placements().size());
        assertEquals(3, result.placements().get(0).weekday());
        assertNull(result.placements().get(0).notice());
        assertEquals(1, result.placements().get(1).weekday());
        assertEquals("国庆节（法定假日）", result.placements().get(1).notice());
    }

    @Test
    void teacherWeeklyCapStopsTheSecondLesson() {
        stubWorld(List.of(slot(SLOT_MON_1, 1, "上午1", LocalTime.of(8, 0)), slot(SLOT_MON_2, 1, "上午2",
            LocalTime.of(10, 0))), List.of(plan(CLASS_A, ROOM_A, "[300]")),
            List.of(teacher(TEACHER_A, "张老师", 1, null)), List.of(classroom(ROOM_A)), List.of(), List.of());
        ScheduleCompletenessResponse report = report(targetRow(CLASS_A, "A班", 2));

        ScheduleGapPlanner.Result result = planner.plan(INSTITUTION, CAMPUS, MONDAY, SUNDAY, List.of(),
            null, report);

        assertEquals(1, result.placements().size());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("本周已达 1 节上限"),
            "未排上的原因要说出是谁的上限拦住了：" + result.failures().get(0).reason());
    }

    @Test
    void dailyCapPerClassStopsTheSecondLessonOfTheSameDay() {
        stubWorld(List.of(slot(SLOT_MON_1, 1, "上午1", LocalTime.of(8, 0)), slot(SLOT_MON_2, 1, "上午2",
            LocalTime.of(10, 0))), List.of(plan(CLASS_A, ROOM_A, "[300]")),
            List.of(teacher(TEACHER_A, "张老师", null, null)), List.of(classroom(ROOM_A)), List.of(), List.of());
        ScheduleCompletenessResponse report = report(targetRow(CLASS_A, "A班", 2));

        ScheduleGapPlanner.Result result = planner.plan(INSTITUTION, CAMPUS, MONDAY, SUNDAY, List.of(),
            new ScheduleAutoStrategy(null, null, 1), report);

        assertEquals(1, result.placements().size());
        assertTrue(result.failures().get(0).reason().contains("每日 1 节上限"),
            "该班当日上限拦住时要说出来：" + result.failures().get(0).reason());
    }

    @Test
    void forbiddenRuleCellIsSkippedForAFreeOne() {
        stubWorld(List.of(slot(SLOT_MON_1, 1, "上午1", LocalTime.of(8, 0)), slot(SLOT_MON_2, 1, "上午2",
            LocalTime.of(10, 0))), List.of(plan(CLASS_A, ROOM_A, "[300]")),
            List.of(teacher(TEACHER_A, "张老师", null, null)), List.of(classroom(ROOM_A)), List.of(), List.of());
        ScheduleRuleSupport.Rule forbidden = new ScheduleRuleSupport.Rule(ScheduleRuleSupport.RULE_FORBIDDEN,
            ScheduleRuleSupport.TARGET_CLASS, CLASS_A, null, null, "周一上午有教研",
            List.of(new ScheduleSlotCell(1, SLOT_MON_1)));
        when(ruleSupport.loadRuleSet(any(), any(), any(), any())).thenReturn(
            new ScheduleRuleSupport.RuleSet(List.of(forbidden), Map.of(SLOT_MON_1, "上午1"), Map.of()));
        ScheduleCompletenessResponse report = report(gapRow(CLASS_A, "A班", 1,
            new ScheduleCompletenessResponse.Gap(MONDAY, 1, SLOT_MON_1, "上午1")));

        ScheduleGapPlanner.Result result = planner.plan(INSTITUTION, CAMPUS, MONDAY, SUNDAY, List.of(),
            null, report);

        assertEquals(1, result.placements().size());
        assertEquals(SLOT_MON_2, result.placements().get(0).timeSlotId());
        assertEquals("free_cell", result.placements().get(0).source());
    }

    @Test
    void classWithConflictingClassroomsIsReturnedToTheOperator() {
        stubWorld(List.of(slot(SLOT_MON_1, 1, "上午1", LocalTime.of(8, 0))),
            List.of(plan(CLASS_A, ROOM_A, "[300]"), plan(CLASS_A, ROOM_B, "[300]")),
            List.of(teacher(TEACHER_A, "张老师", null, null)), List.of(classroom(ROOM_A), classroom(ROOM_B)),
            List.of(), List.of());
        ScheduleCompletenessResponse report = report(targetRow(CLASS_A, "A班", 1));

        ScheduleGapPlanner.Result result = planner.plan(INSTITUTION, CAMPUS, MONDAY, SUNDAY, List.of(),
            null, report);

        assertEquals(0, result.requestedCount());
        assertTrue(result.placements().isEmpty());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("不同教室"),
            "整班不纳入要说清为什么：" + result.failures().get(0).reason());
        assertNull(result.failures().get(0).weekNo());
    }

    @Test
    void classWithoutActivePlanIsNotGuessed() {
        stubWorld(List.of(slot(SLOT_MON_1, 1, "上午1", LocalTime.of(8, 0))), List.of(),
            List.of(teacher(TEACHER_A, "张老师", null, null)), List.of(classroom(ROOM_A)), List.of(), List.of());
        ScheduleCompletenessResponse report = report(targetRow(CLASS_A, "A班", 1));

        ScheduleGapPlanner.Result result = planner.plan(INSTITUTION, CAMPUS, MONDAY, SUNDAY, List.of(),
            null, report);

        assertTrue(result.placements().isEmpty());
        assertTrue(result.failures().get(0).reason().contains("没有启用中的排课计划"),
            result.failures().get(0).reason());
    }

    // ---- 桩数据装配 ----

    private void stubWorld(List<TimeSlotEntity> slots, List<SchedulePlanEntity> plans,
                           List<TeacherEntity> teachers, List<ClassroomEntity> classrooms,
                           List<ScheduleLessonEntity> occupyingLessons,
                           List<ScheduleTeacherRelEntity> occupyingRelations) {
        when(timeSlotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(slots);
        when(planMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(plans);
        when(teacherMapper.selectBatchIds(any())).thenReturn(teachers);
        when(classroomMapper.selectBatchIds(any())).thenReturn(classrooms);
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(occupyingLessons);
        when(teacherRelMapper.selectOccupiedLessonIdsByTeachers(any(), any(), any()))
            .thenReturn(occupyingLessons.stream().map(ScheduleLessonEntity::getId).toList());
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(occupyingRelations);
        when(calendarService.closedDays(any(), any(), any(), any())).thenReturn(Map.of());
        when(ruleSupport.loadRuleSet(any(), any(), any(), any()))
            .thenReturn(new ScheduleRuleSupport.RuleSet(List.of(), Map.of(), Map.of()));
    }

    private TimeSlotEntity slot(Long id, int weekday, String name, LocalTime start) {
        TimeSlotEntity slot = new TimeSlotEntity();
        slot.setId(id);
        slot.setInstitutionId(INSTITUTION);
        slot.setCampusId(CAMPUS);
        slot.setSlotName(name);
        slot.setWeekday(weekday);
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(60));
        slot.setSlotType("lesson");
        slot.setStatus("active");
        return slot;
    }

    private SchedulePlanEntity plan(Long classId, Long classroomId, String teacherIdsJson) {
        SchedulePlanEntity plan = new SchedulePlanEntity();
        plan.setId(classId * 10 + classroomId);
        plan.setInstitutionId(INSTITUTION);
        plan.setCampusId(CAMPUS);
        plan.setClassId(classId);
        plan.setClassroomId(classroomId);
        plan.setTeacherIdsJson(teacherIdsJson);
        plan.setStartDate(MONDAY.minusDays(30));
        plan.setEndDate(SUNDAY.plusDays(30));
        plan.setStatus("active");
        return plan;
    }

    private TeacherEntity teacher(Long id, String name, Integer maxWeekly, Integer maxDaily) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(id);
        teacher.setInstitutionId(INSTITUTION);
        teacher.setTeacherName(name);
        teacher.setMaxWeeklyLessons(maxWeekly);
        teacher.setMaxDailyLessons(maxDaily);
        teacher.setStatus("active");
        return teacher;
    }

    private ClassroomEntity classroom(Long id) {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(id);
        classroom.setCampusId(CAMPUS);
        classroom.setClassroomName("教室" + id);
        classroom.setStatus("active");
        return classroom;
    }

    /** B 班在 ROOM_B 上的一节既有课，只用来制造"这一格被别人占了"的事实。 */
    private ScheduleLessonEntity occupiedLesson() {
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setId(OCCUPYING_LESSON_ID);
        lesson.setInstitutionId(INSTITUTION);
        lesson.setCampusId(CAMPUS);
        lesson.setClassId(CLASS_B);
        lesson.setClassroomId(ROOM_B);
        lesson.setTimeSlotId(SLOT_MON_1);
        lesson.setLessonDate(MONDAY);
        lesson.setLessonWeekday(1);
        lesson.setLessonStatus("scheduled");
        return lesson;
    }

    private ScheduleTeacherRelEntity relation(Long lessonId, Long teacherId) {
        ScheduleTeacherRelEntity relation = new ScheduleTeacherRelEntity();
        relation.setId(lessonId * 10 + teacherId);
        relation.setLessonId(lessonId);
        relation.setTeacherId(teacherId);
        return relation;
    }

    /** 缺口来自计划展开：gaps 里有具体格子，引擎优先补它。 */
    private ScheduleCompletenessResponse.ClassRow gapRow(Long classId, String className, int missing,
                                                         ScheduleCompletenessResponse.Gap... gaps) {
        return row(classId, className, cell(true, missing, missing, List.of(gaps)));
    }

    /** 缺口来自班级课时目标：只知"这周还差 N 节"，落在哪由引擎挑。 */
    private ScheduleCompletenessResponse.ClassRow targetRow(Long classId, String className, int missing) {
        return row(classId, className, cell(true, missing, missing, List.of()));
    }

    private ScheduleCompletenessResponse.WeekCell cell(boolean covered, int expected, int missing,
                                                       List<ScheduleCompletenessResponse.Gap> gaps) {
        return new ScheduleCompletenessResponse.WeekCell(covered, expected, expected - missing, missing,
            0, 0, 0, gaps);
    }

    private ScheduleCompletenessResponse.ClassRow row(Long classId, String className,
                                                      ScheduleCompletenessResponse.WeekCell cell) {
        return new ScheduleCompletenessResponse.ClassRow(classId, className, CAMPUS, 1L, 1, "weekly_target", 2, null, null, null, null,
            List.of(cell), new ScheduleCompletenessResponse.RowSummary(cell.expected(), cell.generated(),
                cell.completed(), cell.missing(), cell.extra()));
    }

    private ScheduleCompletenessResponse report(ScheduleCompletenessResponse.ClassRow... rows) {
        List<ScheduleCompletenessResponse.WeekColumn> weeks =
            List.of(new ScheduleCompletenessResponse.WeekColumn(1, MONDAY, SUNDAY));
        return new ScheduleCompletenessResponse(MONDAY, SUNDAY, weeks, List.of(rows),
            new ScheduleCompletenessResponse.Summary(rows.length, 0, 0, 0, 0, 0, 0, 0));
    }

}
