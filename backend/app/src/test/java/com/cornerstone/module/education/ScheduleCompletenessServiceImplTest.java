package com.cornerstone.module.education;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.SchedulePlanEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ScheduleCompletenessQuery;
import com.cornerstone.module.education.dto.ScheduleCompletenessResponse;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.SchedulePlanMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.impl.ScheduleCompletenessServiceImpl;
import com.cornerstone.module.education.service.support.SchedulePlanExpander;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排课完成度汇总口径：应排来自计划展开，实排按「日期#时段」键匹配，
 * 缺口/计划外/停课/完成各自独立计数。
 */
class ScheduleCompletenessServiceImplTest {

    /** 2026-06-01 是周一 */
    private static final LocalDate WEEK_ONE = LocalDate.of(2026, 6, 1);
    private static final LocalDate WEEK_TWO = LocalDate.of(2026, 6, 8);
    private static final LocalDate RANGE_END = LocalDate.of(2026, 6, 14);

    private final SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
    private final ScheduleLessonMapper lessonMapper = mock(ScheduleLessonMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
    private final TermMapper termMapper = mock(TermMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ScheduleCompletenessServiceImpl service = new ScheduleCompletenessServiceImpl(
        planMapper, lessonMapper, classMapper, timeSlotMapper, termMapper, objectMapper);

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SchedulePlanEntity.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleLessonEntity.class);
        TableInfoHelper.initTableInfo(assistant, TeachingClassEntity.class);
        TableInfoHelper.initTableInfo(assistant, TimeSlotEntity.class);
    }

    @Test
    void missingInstitutionOnLoginIsRejected() {
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.report(null, query(WEEK_ONE, RANGE_END)));
        assertTrue(error.getMessage().contains("未绑定机构"));
    }

    @Test
    void otherInstitutionIsRejectedBeforeAnyQuery() {
        ScheduleCompletenessQuery query = query(WEEK_ONE, RANGE_END);
        query.setInstitutionId(999L);
        BusinessException error = assertThrows(BusinessException.class, () -> service.report(1L, query));
        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        assertTrue(error.getMessage().contains("无权查看"));
        verify(planMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void emptyPlansStillReturnWeekColumns() {
        when(planMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ScheduleCompletenessResponse response = service.report(1L, query(WEEK_ONE, RANGE_END));

        assertEquals(2, response.weeks().size());
        assertEquals(WEEK_ONE, response.weeks().getFirst().weekStart());
        assertEquals(RANGE_END, response.weekTo());
        assertTrue(response.classes().isEmpty());
        assertEquals(0, response.summary().expected());
    }

    @Test
    void aggregatesExpectedGeneratedMissingPerWeek() {
        stubMasterData(plan(null));
        // 计划是周一上1 + 周二上2：第一周两格都生成了，第二周还没生成
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            lesson(WEEK_ONE, 500L, "scheduled"),
            lesson(WEEK_ONE.plusDays(1), 501L, "completed")
        ));

        ScheduleCompletenessResponse response = service.report(1L, query(WEEK_ONE, RANGE_END));

        ScheduleCompletenessResponse.ClassRow row = response.classes().getFirst();
        assertEquals("测试班级", row.className());
        assertEquals(1, row.planCount());
        assertEquals(2, response.weeks().size());
        ScheduleCompletenessResponse.WeekCell first = row.cells().getFirst();
        assertEquals(2, first.expected());
        assertEquals(2, first.generated());
        assertEquals(1, first.completed());
        assertTrue(first.gaps().isEmpty());
        ScheduleCompletenessResponse.WeekCell second = row.cells().get(1);
        assertEquals(2, second.expected());
        assertEquals(0, second.generated());
        assertEquals(2, second.gaps().size());
        assertEquals(WEEK_TWO, second.gaps().getFirst().lessonDate());
        assertEquals("上1", second.gaps().getFirst().slotName());
        assertEquals(4, row.summary().expected());
        assertEquals(2, row.summary().generated());
        assertEquals(2, row.summary().missing());
        assertEquals(4, response.summary().expected());
        assertEquals(50, response.summary().generatedPercent());
        assertEquals(25, response.summary().completedPercent());
    }

    @Test
    void cancelledLessonStillCountsAsGenerated() {
        stubMasterData(plan(null));
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            lesson(WEEK_ONE, 500L, "cancelled")
        ));

        ScheduleCompletenessResponse.WeekCell first =
            service.report(1L, query(WEEK_ONE, RANGE_END)).classes().getFirst().cells().getFirst();

        // 停课占住了那一格：算已生成，不算缺口，单列显示
        assertEquals(1, first.generated());
        assertEquals(1, first.cancelled());
        assertEquals(1, first.gaps().size());
        assertEquals(WEEK_ONE.plusDays(1), first.gaps().getFirst().lessonDate());
        assertEquals(501L, first.gaps().getFirst().timeSlotId());
    }

    @Test
    void duplicateLessonInOneCellNeverInflatesGenerated() {
        stubMasterData(plan(null));
        // 结课会让出格子（见 V72 的占用防重键），同一格再排一节课是合法数据：
        // 完成度按格子计数，绝不能出现"已生成 > 应排"或 100% 与缺口同屏
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            lesson(WEEK_ONE, 500L, "completed"),
            lesson(WEEK_ONE, 500L, "scheduled")
        ));

        ScheduleCompletenessResponse response = service.report(1L, query(WEEK_ONE, RANGE_END));
        ScheduleCompletenessResponse.WeekCell first = response.classes().getFirst().cells().getFirst();

        assertEquals(2, first.expected());
        assertEquals(1, first.generated());
        assertEquals(1, first.completed());
        assertTrue(first.generated() <= first.expected());
        assertTrue(response.summary().generated() <= response.summary().expected());
        assertTrue(response.summary().generatedPercent() < 100);
    }

    @Test
    void skipDatesAreNotExpectedAndOffPlanLessonCountsAsExtra() {
        stubMasterData(plan("[\"2026-06-01\"]"));
        // 6/1 被计划跳过，6/3 是手工加排：既不算补上缺口，也要露出来
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            lesson(WEEK_ONE, 502L, "scheduled")
        ));

        ScheduleCompletenessResponse.WeekCell first =
            service.report(1L, query(WEEK_ONE, RANGE_END)).classes().getFirst().cells().getFirst();

        assertEquals(1, first.expected());
        assertEquals(0, first.generated());
        assertEquals(1, first.extra());
        assertEquals(1, first.gaps().size());
        assertEquals(501L, first.gaps().getFirst().timeSlotId());
    }

    @Test
    void oddWeekPlanOnlyFillsOddColumns() {
        stubMasterData(plan(null, SchedulePlanExpander.ODD_WEEK));
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<ScheduleCompletenessResponse.WeekCell> cells =
            service.report(1L, query(WEEK_ONE, RANGE_END)).classes().getFirst().cells();

        assertEquals(2, cells.get(0).expected());
        assertEquals(0, cells.get(1).expected());
        // 双周列没排任何格子，自然也没有缺口
        assertTrue(cells.get(1).gaps().isEmpty());
    }

    @Test
    void datesAlignToWholeWeeksAndRangeCappedAt26Weeks() {
        when(planMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        // 6/3(周三)~6/4(周四) 属于同一自然周，应对齐成 6/1~6/7
        ScheduleCompletenessResponse aligned = service.report(1L,
            query(LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 4)));
        assertEquals(WEEK_ONE, aligned.weekFrom());
        assertEquals(LocalDate.of(2026, 6, 7), aligned.weekTo());
        assertEquals(1, aligned.weeks().size());

        BusinessException error = assertThrows(BusinessException.class, () -> service.report(1L,
            query(WEEK_ONE, WEEK_ONE.plusWeeks(26).plusDays(6))));
        assertTrue(error.getMessage().contains("26"));
    }

    @Test
    void termRangeUsedWhenDatesOmitted() {
        TermEntity term = new TermEntity();
        term.setStartDate(LocalDate.of(2026, 3, 2));
        term.setEndDate(LocalDate.of(2026, 6, 28));
        when(termMapper.selectById(20L)).thenReturn(term);
        when(planMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ScheduleCompletenessQuery query = new ScheduleCompletenessQuery();
        query.setTermId(20L);
        ScheduleCompletenessResponse response = service.report(1L, query);

        // 2026-03-02 是周一，到 6/28(周日) 共 17 周
        assertEquals(LocalDate.of(2026, 3, 2), response.weekFrom());
        assertEquals(LocalDate.of(2026, 6, 28), response.weekTo());
        assertEquals(17, response.weeks().size());
    }

    @Test
    void defaultWindowIsTwelveWeeksFromThisMonday() {
        when(planMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ScheduleCompletenessResponse response = service.report(1L, new ScheduleCompletenessQuery());

        assertEquals(LocalDate.now().with(java.time.DayOfWeek.MONDAY), response.weekFrom());
        assertEquals(12, response.weeks().size());
    }

    // ------------------------------------------------------------------ 夹具

    private void stubMasterData(SchedulePlanEntity plan) {
        when(planMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(plan));

        TeachingClassEntity clazz = new TeachingClassEntity();
        clazz.setId(100L);
        clazz.setInstitutionId(1L);
        clazz.setCampusId(10L);
        clazz.setTermId(20L);
        clazz.setClassName("测试班级");
        when(classMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(clazz));

        when(timeSlotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            timeSlot(500L, "上1"), timeSlot(501L, "上2"), timeSlot(502L, "下1")
        ));
    }

    private SchedulePlanEntity plan(String skipDatesJson) {
        return plan(skipDatesJson, null);
    }

    private SchedulePlanEntity plan(String skipDatesJson, String weekFrequency) {
        SchedulePlanEntity plan = new SchedulePlanEntity();
        plan.setInstitutionId(1L);
        plan.setCampusId(10L);
        plan.setTermId(20L);
        plan.setClassId(100L);
        plan.setSlotsJson("[{\"weekday\":1,\"timeSlotId\":500},{\"weekday\":2,\"timeSlotId\":501}]");
        plan.setWeekFrequency(weekFrequency);
        plan.setStartDate(WEEK_ONE);
        plan.setEndDate(RANGE_END);
        plan.setSkipDatesJson(skipDatesJson);
        plan.setStatus("active");
        return plan;
    }

    private ScheduleLessonEntity lesson(LocalDate date, Long timeSlotId, String status) {
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setInstitutionId(1L);
        lesson.setClassId(100L);
        lesson.setLessonDate(date);
        lesson.setTimeSlotId(timeSlotId);
        lesson.setLessonStatus(status);
        return lesson;
    }

    private TimeSlotEntity timeSlot(Long id, String slotName) {
        TimeSlotEntity slot = new TimeSlotEntity();
        slot.setId(id);
        slot.setInstitutionId(1L);
        slot.setCampusId(10L);
        slot.setWeekday(1);
        slot.setSlotName(slotName);
        return slot;
    }

    private ScheduleCompletenessQuery query(LocalDate from, LocalDate to) {
        ScheduleCompletenessQuery query = new ScheduleCompletenessQuery();
        query.setInstitutionId(1L);
        query.setDateFrom(from);
        query.setDateTo(to);
        return query;
    }
}
