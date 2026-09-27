package com.cornerstone.module.education;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ScheduleAutoBatchEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ScheduleAutoBatchRequest;
import com.cornerstone.module.education.dto.ScheduleAutoFailure;
import com.cornerstone.module.education.dto.ScheduleAutoPlanResponse;
import com.cornerstone.module.education.dto.ScheduleAutoPlacement;
import com.cornerstone.module.education.dto.ScheduleAutoRollbackResponse;
import com.cornerstone.module.education.dto.ScheduleAutoStrategy;
import com.cornerstone.module.education.dto.ScheduleCompletenessResponse;
import com.cornerstone.module.education.mapper.ScheduleAutoBatchMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.service.ScheduleCompletenessService;
import com.cornerstone.module.education.service.impl.ScheduleAutoBatchServiceImpl;
import com.cornerstone.module.education.service.support.ScheduleGapPlanner;
import com.cornerstone.module.education.service.support.ScheduleTeacherLock;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 补排批次的编排口径：机构归属先于一切查询、一节没排上就不留批次行、撤销只动"还没上"的课次。
 */
class ScheduleAutoBatchServiceImplTest {

    private static final long INSTITUTION = 1L;
    private static final long OTHER_INSTITUTION = 999L;
    private static final long CAMPUS = 10L;
    private static final long TERM = 20L;
    private static final long CLASS_A = 100L;
    private static final long CLASS_TERM = 77L;
    private static final long ROOM = 200L;
    private static final long TEACHER_A = 300L;
    private static final long BATCH = 500L;
    private static final LocalDate FROM = LocalDate.now().plusDays(1);
    private static final LocalDate TO = LocalDate.now().plusDays(8);

    private final ScheduleAutoBatchMapper batchMapper = mock(ScheduleAutoBatchMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final TermMapper termMapper = mock(TermMapper.class);
    private final ScheduleLessonMapper lessonMapper = mock(ScheduleLessonMapper.class);
    private final ScheduleTeacherRelMapper teacherRelMapper = mock(ScheduleTeacherRelMapper.class);
    private final ScheduleCompletenessService completenessService = mock(ScheduleCompletenessService.class);
    private final ScheduleGapPlanner planner = mock(ScheduleGapPlanner.class);
    private final ScheduleTeacherLock teacherLock = mock(ScheduleTeacherLock.class);
    private final ScheduleAutoBatchServiceImpl service = new ScheduleAutoBatchServiceImpl(batchMapper, classMapper,
        campusMapper, termMapper, lessonMapper, teacherRelMapper, completenessService, planner, teacherLock,
        new ObjectMapper());

    @BeforeAll
    static void initEntityTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ScheduleAutoBatchEntity.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleLessonEntity.class);
        TableInfoHelper.initTableInfo(assistant, TeachingClassEntity.class);
    }

    @BeforeEach
    void logIn() {
        // 三参构造才会把 authenticated 置为 true，两参的那枚是"未认证"形态
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUser(9L, INSTITUTION, "op", Set.of(), Set.of(), false, "pc"), "n/a", List.of()));
        when(campusMapper.selectById(CAMPUS)).thenReturn(campus());
        when(termMapper.selectById(TERM)).thenReturn(term());
        when(completenessService.report(any(), any())).thenReturn(emptyReport());
    }

    @AfterEach
    void logOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void windowStartingBeforeTodayIsRejected() {
        ScheduleAutoBatchRequest request = request(FROM.minusDays(3), TO);
        BusinessException error = assertThrows(BusinessException.class, () -> service.preview(request));
        assertTrue(error.getMessage().contains("起始日期不能早于今天"), error.getMessage());
    }

    @Test
    void otherInstitutionIsRejectedBeforeAnyPlanning() {
        ScheduleAutoBatchRequest request = request(FROM, TO);
        BusinessException error = assertThrows(BusinessException.class, () -> service.preview(
            new ScheduleAutoBatchRequest(OTHER_INSTITUTION, request.campusId(), request.termId(),
                request.startDate(), request.endDate(), request.classIds(), request.strategy(), request.remark())));
        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        verify(planner, never()).plan(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void classFromAnotherCampusIsRejected() {
        TeachingClassEntity otherCampusClass = teachingClass();
        otherCampusClass.setCampusId(999L);
        when(classMapper.selectById(CLASS_A)).thenReturn(otherCampusClass);
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.preview(new ScheduleAutoBatchRequest(INSTITUTION, CAMPUS, TERM, FROM, TO,
                List.of(CLASS_A), null, null)));
        assertEquals(ResultCode.BAD_REQUEST, error.getResultCode());
        assertTrue(error.getMessage().contains("不在所选校区"), error.getMessage());
    }

    @Test
    void emptyDraftCreatesNoBatch() {
        when(planner.plan(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new ScheduleGapPlanner.Result(List.of(), List.of(), 3));

        ScheduleAutoPlanResponse response = service.run(request(FROM, TO));

        assertTrue(response.executed());
        assertEquals(0, response.generatedCount());
        verify(batchMapper, never()).insert(any(ScheduleAutoBatchEntity.class));
        verify(lessonMapper, never()).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void runWritesLessonsWithClassTermAndBatchId() {
        when(planner.plan(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new ScheduleGapPlanner.Result(List.of(placement()), List.of(), 1));
        when(classMapper.selectBatchIds(any())).thenReturn(List.of(teachingClass()));
        when(batchMapper.insert(any(ScheduleAutoBatchEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ScheduleAutoBatchEntity.class).setId(BATCH);
            return 1;
        });

        ScheduleAutoPlanResponse response = service.run(request(FROM, TO));

        assertEquals(BATCH, response.batchId());
        verify(teacherLock).lock(List.of(TEACHER_A));
        ArgumentCaptor<ScheduleLessonEntity> inserted = ArgumentCaptor.forClass(ScheduleLessonEntity.class);
        verify(lessonMapper).insert(inserted.capture());
        ScheduleLessonEntity lesson = inserted.getValue();
        assertEquals(CLASS_TERM, lesson.getTermId());
        assertEquals(BATCH, lesson.getAutoBatchId());
        assertEquals("scheduled", lesson.getLessonStatus());
        verify(teacherRelMapper).insert(any(ScheduleTeacherRelEntity.class));
    }

    @Test
    void unscheduledCountsLessonsRatherThanFailureRows() {
        // 一周缺 3 节、第 2 节起排不下去就整周停止尝试：只有 1 条原因，但欠的是 2 节
        when(planner.plan(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new ScheduleGapPlanner.Result(List.of(placement()),
                List.of(new ScheduleAutoFailure(CLASS_A, "补排测试班", 1, "教师本周已达 1 节上限")), 3));

        ScheduleAutoPlanResponse response = service.preview(request(FROM, TO));

        assertEquals(3, response.requestedCount());
        assertEquals(1, response.generatedCount());
        assertEquals(2, response.unscheduledCount());
    }

    @Test
    void rollbackKeepsLessonsAlreadyTaughtOrPast() {
        when(batchMapper.selectById(BATCH)).thenReturn(batch());
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            lesson(1L, FROM.plusDays(1), "scheduled"),
            lesson(2L, FROM.plusDays(2), "completed"),
            lesson(3L, LocalDate.now().minusDays(1), "scheduled")));

        ScheduleAutoRollbackResponse response = service.rollback(BATCH);

        assertEquals(1, response.canceledCount());
        assertEquals(2, response.keptCount());
        assertTrue(response.message().contains("已开课或已过上课日"), response.message());
        // 留给操作员看的话里不能出现状态码
        assertTrue(response.message().contains("已结课"), response.message());
        assertFalse(response.message().contains("completed"), response.message());
        verify(teacherRelMapper).physicalDeleteByLessonId(1L);
        verify(lessonMapper).deleteById(1L);
        verify(lessonMapper, never()).deleteById(2L);
        ArgumentCaptor<ScheduleAutoBatchEntity> saved = ArgumentCaptor.forClass(ScheduleAutoBatchEntity.class);
        verify(batchMapper).updateById(saved.capture());
        assertEquals("rolled_back", saved.getValue().getStatus());
    }

    @Test
    void rolledBackBatchCannotBeRolledBackAgain() {
        ScheduleAutoBatchEntity batch = batch();
        batch.setStatus("rolled_back");
        when(batchMapper.selectById(BATCH)).thenReturn(batch);
        BusinessException error = assertThrows(BusinessException.class, () -> service.rollback(BATCH));
        assertTrue(error.getMessage().contains("已撤销过"), error.getMessage());
    }

    @Test
    void batchOfAnotherInstitutionIsNotFoundRatherThanLeaked() {
        ScheduleAutoBatchEntity batch = batch();
        batch.setInstitutionId(OTHER_INSTITUTION);
        when(batchMapper.selectById(BATCH)).thenReturn(batch);
        BusinessException error = assertThrows(BusinessException.class, () -> service.rollback(BATCH));
        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
    }

    // ---- 桩数据 ----

    private ScheduleAutoBatchRequest request(LocalDate from, LocalDate to) {
        return new ScheduleAutoBatchRequest(INSTITUTION, CAMPUS, TERM, from, to, List.of(),
            new ScheduleAutoStrategy(null, null, null), "补国庆后的缺口");
    }

    private CampusEntity campus() {
        CampusEntity campus = new CampusEntity();
        campus.setId(CAMPUS);
        campus.setInstitutionId(INSTITUTION);
        campus.setCampusName("主校区");
        campus.setStatus("active");
        return campus;
    }

    private TermEntity term() {
        TermEntity term = new TermEntity();
        term.setId(TERM);
        term.setInstitutionId(INSTITUTION);
        term.setTermName("2026 秋季");
        term.setStartDate(FROM.minusMonths(2));
        term.setEndDate(TO.plusMonths(3));
        return term;
    }

    private TeachingClassEntity teachingClass() {
        TeachingClassEntity entity = new TeachingClassEntity();
        entity.setId(CLASS_A);
        entity.setInstitutionId(INSTITUTION);
        entity.setCampusId(CAMPUS);
        entity.setTermId(CLASS_TERM);
        entity.setClassName("A班");
        return entity;
    }

    private ScheduleAutoBatchEntity batch() {
        ScheduleAutoBatchEntity batch = new ScheduleAutoBatchEntity();
        batch.setId(BATCH);
        batch.setInstitutionId(INSTITUTION);
        batch.setCampusId(CAMPUS);
        batch.setTermId(TERM);
        batch.setStartDate(FROM);
        batch.setEndDate(TO);
        batch.setRequestedCount(3);
        batch.setGeneratedCount(3);
        batch.setUnscheduledCount(0);
        batch.setStatus("completed");
        return batch;
    }

    private ScheduleLessonEntity lesson(Long id, LocalDate date, String status) {
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setId(id);
        lesson.setInstitutionId(INSTITUTION);
        lesson.setClassId(CLASS_A);
        lesson.setLessonDate(date);
        lesson.setLessonStatus(status);
        lesson.setAutoBatchId(BATCH);
        return lesson;
    }

    private ScheduleAutoPlacement placement() {
        return new ScheduleAutoPlacement(CLASS_A, "A班", 1, FROM, FROM.getDayOfWeek().getValue(), 1001L, "上午1",
            LocalTime.of(8, 0), LocalTime.of(9, 0), ROOM, "教室1", List.of(TEACHER_A), "张老师",
            "plan_gap", null);
    }

    private ScheduleCompletenessResponse emptyReport() {
        return new ScheduleCompletenessResponse(FROM, TO, List.of(), List.of(),
            new ScheduleCompletenessResponse.Summary(0, 0, 0, 0, 0, 0, 0, 0));
    }
}
