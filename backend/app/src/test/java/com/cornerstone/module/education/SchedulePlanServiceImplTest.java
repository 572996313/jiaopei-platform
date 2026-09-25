package com.cornerstone.module.education;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.SchedulePlanEntity;
import com.cornerstone.module.education.domain.entity.ScheduleRuleEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.SchedulePlanCommitRequest;
import com.cornerstone.module.education.dto.SchedulePlanCommitResponse;
import com.cornerstone.module.education.dto.SchedulePlanOccurrence;
import com.cornerstone.module.education.dto.SchedulePlanPreviewResponse;
import com.cornerstone.module.education.dto.SchedulePlanRequest;
import com.cornerstone.module.education.dto.ScheduleSlotCell;
import com.cornerstone.module.education.dto.SchedulePlanView;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.SchedulePlanMapper;
import com.cornerstone.module.education.mapper.ScheduleRuleMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.impl.SchedulePlanServiceImpl;
import com.cornerstone.module.education.service.support.ScheduleRuleSupport;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulePlanServiceImplTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final LocalDate END = LocalDate.of(2026, 6, 14);
    private static final ScheduleSlotCell MONDAY_SLOT = new ScheduleSlotCell(1, 500L);

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ScheduleLessonEntity.class);
        TableInfoHelper.initTableInfo(assistant, SchedulePlanEntity.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleRuleEntity.class);
        TableInfoHelper.initTableInfo(assistant, TimeSlotEntity.class);
        // 规则提示语要带出班级/教师名称，ScheduleRuleSupport 里也会按这两张表建 wrapper
        TableInfoHelper.initTableInfo(assistant, TeachingClassEntity.class);
        TableInfoHelper.initTableInfo(assistant, TeacherEntity.class);
    }

    private final SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
    private final TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
    private final ScheduleLessonMapper lessonMapper = mock(ScheduleLessonMapper.class);
    private final ScheduleTeacherRelMapper teacherRelMapper = mock(ScheduleTeacherRelMapper.class);
    private final ScheduleRuleMapper ruleMapper = mock(ScheduleRuleMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ScheduleRuleSupport ruleSupport =
        new ScheduleRuleSupport(ruleMapper, classMapper, teacherMapper, timeSlotMapper, objectMapper);
    private final SchedulePlanServiceImpl service = new SchedulePlanServiceImpl(
        planMapper, classMapper, teacherMapper, classroomMapper,
        timeSlotMapper, lessonMapper, teacherRelMapper, ruleSupport, objectMapper
    );

    @Test
    void previewMarksConflictAndSkippedDates() {
        stubValidMasterData();
        // 班级/教室两个范围查询都返回 6/1 已被占用的课次
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(occupiedLesson(START)));

        SchedulePlanPreviewResponse response = service.preview(request(List.of(LocalDate.of(2026, 6, 8))));

        assertEquals(2, response.totalCount());
        assertEquals(1, response.conflictCount());
        assertEquals(1, response.skippedCount());
        SchedulePlanPreviewResponse.Item first = response.items().getFirst();
        assertTrue(first.conflict());
        assertFalse(first.suggestedChecked());
        assertEquals(2, first.conflictMessages().size());
        SchedulePlanPreviewResponse.Item second = response.items().get(1);
        assertEquals(LocalDate.of(2026, 6, 8), second.lessonDate());
        assertTrue(second.skipped());
        assertFalse(second.suggestedChecked());
        assertTrue(response.ruleNotices().isEmpty());
    }

    @Test
    void previewMarksForbiddenRuleCellAsConflict() {
        stubValidMasterData();
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(rule("forbidden", "class", 100L, "[{\"weekday\":1,\"timeSlotId\":500}]")), List.of());

        SchedulePlanPreviewResponse response = service.preview(request(List.of()));

        // 禁排按"星期×节次"命中，展开出的两周课次全算冲突
        assertEquals(2, response.conflictCount());
        SchedulePlanPreviewResponse.Item first = response.items().getFirst();
        String message = first.conflictMessages().stream()
            .filter(text -> text.contains("禁排"))
            .findFirst()
            .orElse("");
        assertFalse(message.isEmpty());
        // 提示语给操作员看，必须带班级名称而不是 19 位雪花 ID
        assertTrue(message.contains("班级「测试班级」"), message);
        assertFalse(message.contains("100"), message);
        assertFalse(first.suggestedChecked());
    }

    @Test
    void previewNoticesRequiredRuleCellMissingFromPlanPattern() {
        stubValidMasterData();
        // 必排在周三第2节，计划的每周模式只有周一：只提示，不算冲突、不拦提交
        // loadRuleSet 先查班级规则再查教师规则，所以两次返回分别是班级规则与空
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(rule("required", "class", 100L, "[{\"weekday\":3,\"timeSlotId\":501}]")), List.of());

        SchedulePlanPreviewResponse response = service.preview(request(List.of()));

        assertEquals(1, response.ruleNotices().size());
        String notice = response.ruleNotices().getFirst();
        assertTrue(notice.contains("必排"));
        assertTrue(notice.contains("周三"));
        assertTrue(notice.contains("班级「测试班级」"), notice);
        assertEquals(0, response.conflictCount());
    }

    @Test
    void commitNewPlanWritesLessonsAndTeacherRelations() {
        stubValidMasterData();
        doAnswer(invocation -> {
            SchedulePlanEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        }).when(planMapper).insert(any(SchedulePlanEntity.class));
        AtomicLong lessonIdSeq = new AtomicLong(700L);
        doAnswer(invocation -> {
            ScheduleLessonEntity entity = invocation.getArgument(0);
            entity.setId(lessonIdSeq.getAndIncrement());
            return 1;
        }).when(lessonMapper).insert(any(ScheduleLessonEntity.class));

        SchedulePlanCommitResponse response = service.commit(commitRequest(null, START, LocalDate.of(2026, 6, 8)));

        assertEquals(900L, response.planId());
        assertEquals(2, response.createdCount());
        assertEquals(0, response.skippedCount());

        ArgumentCaptor<SchedulePlanEntity> planCaptor = ArgumentCaptor.forClass(SchedulePlanEntity.class);
        verify(planMapper).insert(planCaptor.capture());
        SchedulePlanEntity savedPlan = planCaptor.getValue();
        assertEquals("[400]", savedPlan.getTeacherIdsJson());
        assertEquals("[{\"weekday\":1,\"timeSlotId\":500}]", savedPlan.getSlotsJson());
        assertEquals("every_week", savedPlan.getWeekFrequency());
        assertEquals("active", savedPlan.getStatus());

        ArgumentCaptor<ScheduleLessonEntity> lessonCaptor = ArgumentCaptor.forClass(ScheduleLessonEntity.class);
        verify(lessonMapper, times(2)).insert(lessonCaptor.capture());
        List<ScheduleLessonEntity> lessons = lessonCaptor.getAllValues();
        assertEquals(List.of(START, LocalDate.of(2026, 6, 8)),
            lessons.stream().map(ScheduleLessonEntity::getLessonDate).toList());
        lessons.forEach(lesson -> {
            assertEquals(900L, lesson.getPlanId());
            assertEquals("scheduled", lesson.getLessonStatus());
            assertEquals(LocalTime.of(9, 0), lesson.getStartTime());
        });
        verify(teacherRelMapper, times(2)).insert(any(ScheduleTeacherRelEntity.class));
    }

    @Test
    void commitRejectsTeacherForbiddenRuleCellAndWritesNothing() {
        stubValidMasterData();
        doAnswer(invocation -> {
            SchedulePlanEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        }).when(planMapper).insert(any(SchedulePlanEntity.class));
        // 教师禁排：班级维度那趟查不到，教师维度才命中
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(), List.of(rule("forbidden", "teacher", 400L, "[{\"weekday\":1,\"timeSlotId\":500}]")));

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.commit(commitRequest(null, START)));

        assertTrue(error.getMessage().contains("禁排"));
        assertTrue(error.getMessage().contains("教师「测试教师」"), error.getMessage());
        verify(lessonMapper, never()).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void commitRejectsSelectionOutsideExpansion() {
        stubValidMasterData();
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.commit(commitRequest(null, LocalDate.of(2026, 6, 10))));
        assertTrue(error.getMessage().contains("不在计划展开范围内"));
        verify(planMapper, never()).insert(any(SchedulePlanEntity.class));
    }

    @Test
    void commitRejectsSelectionHittingSkipDate() {
        stubValidMasterData();
        // 6/8 在跳过日期内:预览只标记不剔除,提交侧必须拒掉,否则前端禁选被绕过后会生成"整日略过"的课次
        SchedulePlanCommitRequest request = new SchedulePlanCommitRequest(
            null,
            request(List.of(LocalDate.of(2026, 6, 8))),
            List.of(new SchedulePlanOccurrence(LocalDate.of(2026, 6, 8), 500L))
        );

        BusinessException error = assertThrows(BusinessException.class, () -> service.commit(request));
        assertTrue(error.getMessage().contains("命中跳过日期"));
        verify(lessonMapper, never()).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void commitSkipsLessonsAlreadyGeneratedByPlan() {
        stubValidMasterData();
        doAnswer(invocation -> {
            SchedulePlanEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        }).when(planMapper).insert(any(SchedulePlanEntity.class));
        // 计划已有课次与占用查询返回同一条 6/1
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(occupiedLesson(START)));

        SchedulePlanCommitResponse response = service.commit(commitRequest(null, START));

        assertEquals(0, response.createdCount());
        assertEquals(1, response.skippedCount());
        assertEquals(1, response.skipMessages().size());
        verify(lessonMapper, never()).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void commitFailsWholeBatchWhenSelectedLessonConflicts() {
        stubValidMasterData();
        doAnswer(invocation -> {
            SchedulePlanEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        }).when(planMapper).insert(any(SchedulePlanEntity.class));
        // 第1次是计划自身课次查重(空),第2/3次是班级/教室占用(6/1 已被手工排课占用)
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(), List.of(occupiedLesson(START)), List.of(occupiedLesson(START)));

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.commit(commitRequest(null, START)));
        assertTrue(error.getMessage().contains("冲突"));
        verify(lessonMapper, never()).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void cancelPlanLessonsDeletesFutureOccupyingLessons() {
        when(planMapper.selectById(900L)).thenReturn(new SchedulePlanEntity());
        ScheduleLessonEntity first = occupiedLesson(LocalDate.now().plusDays(1));
        first.setId(901L);
        ScheduleLessonEntity second = occupiedLesson(LocalDate.now().plusDays(8));
        second.setId(902L);
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));

        assertEquals(2, service.cancelPlanLessons(900L));

        verify(teacherRelMapper).physicalDeleteByLessonId(901L);
        verify(teacherRelMapper).physicalDeleteByLessonId(902L);
        verify(lessonMapper).deleteById(901L);
        verify(lessonMapper).deleteById(902L);
    }

    @Test
    void updatePlanPersistsDefinitionAndReadsJsonBack() {
        stubValidMasterData();
        SchedulePlanEntity existing = new SchedulePlanEntity();
        existing.setId(900L);
        existing.setInstitutionId(1L);
        existing.setCampusId(10L);
        existing.setTermId(20L);
        existing.setClassId(100L);
        existing.setClassroomId(300L);
        when(planMapper.selectById(900L)).thenReturn(existing);

        SchedulePlanView view = service.updatePlan(900L, request(List.of(LocalDate.of(2026, 6, 8))));

        assertEquals(List.of(400L), view.teacherIds());
        assertEquals(List.of(MONDAY_SLOT), view.slots());
        assertEquals("every_week", view.weekFrequency());
        assertEquals(List.of(LocalDate.of(2026, 6, 8)), view.skipDates());
        verify(planMapper).updateById(existing);
    }

    private void stubValidMasterData() {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(100L);
        teachingClass.setInstitutionId(1L);
        teachingClass.setCampusId(10L);
        teachingClass.setTermId(20L);
        teachingClass.setClassName("测试班级");
        when(classMapper.selectById(100L)).thenReturn(teachingClass);
        // 规则提示语按 selectList 取名称（ScheduleRuleSupport#targetNames）
        when(classMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(teachingClass));

        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(300L);
        classroom.setCampusId(10L);
        when(classroomMapper.selectById(300L)).thenReturn(classroom);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(400L);
        teacher.setInstitutionId(1L);
        teacher.setCampusId(10L);
        teacher.setTeacherName("测试教师");
        when(teacherMapper.selectById(400L)).thenReturn(teacher);
        when(teacherMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(teacher));

        TimeSlotEntity timeSlot = new TimeSlotEntity();
        timeSlot.setId(500L);
        timeSlot.setInstitutionId(1L);
        timeSlot.setCampusId(10L);
        timeSlot.setWeekday(1);
        timeSlot.setSlotName("上1");
        timeSlot.setStartTime(LocalTime.of(9, 0));
        timeSlot.setEndTime(LocalTime.of(10, 0));
        when(timeSlotMapper.selectById(500L)).thenReturn(timeSlot);
    }

    private SchedulePlanRequest request(List<LocalDate> skipDates) {
        return new SchedulePlanRequest(1L, 10L, 20L, 100L, 300L, List.of(400L),
            List.of(MONDAY_SLOT), null, START, END, skipDates, null, null);
    }

    private SchedulePlanCommitRequest commitRequest(Long planId, LocalDate... selectedDates) {
        List<SchedulePlanOccurrence> lessons = Arrays.stream(selectedDates)
            .map(date -> new SchedulePlanOccurrence(date, 500L))
            .toList();
        return new SchedulePlanCommitRequest(planId, request(List.of()), lessons);
    }

    private ScheduleLessonEntity occupiedLesson(LocalDate date) {
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setLessonDate(date);
        lesson.setTimeSlotId(500L);
        return lesson;
    }

    private ScheduleRuleEntity rule(String ruleType, String targetType, Long targetId, String slotsJson) {
        ScheduleRuleEntity rule = new ScheduleRuleEntity();
        rule.setInstitutionId(1L);
        rule.setRuleType(ruleType);
        rule.setTargetType(targetType);
        rule.setTargetId(targetId);
        rule.setSlotsJson(slotsJson);
        rule.setStatus("active");
        rule.setRemark("测试规则");
        return rule;
    }
}
