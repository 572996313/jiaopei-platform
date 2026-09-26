package com.cornerstone.module.education;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.ScheduleRuleEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ScheduleLessonPageQuery;
import com.cornerstone.module.education.dto.ScheduleLessonRequest;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleRuleMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.impl.ScheduleLessonServiceImpl;
import com.cornerstone.module.education.service.support.ScheduleRuleSupport;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleLessonServiceImplTest {

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ScheduleLessonEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ScheduleRuleEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TimeSlotEntity.class);
        // 规则提示语要带出班级/教师名称，ScheduleRuleSupport 里也会按这两张表建 wrapper
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TeachingClassEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TeacherEntity.class);
    }

    private final ScheduleLessonMapper lessonMapper = mock(ScheduleLessonMapper.class);
    private final ScheduleTeacherRelMapper teacherRelMapper = mock(ScheduleTeacherRelMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
    private final TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
    private final ScheduleRuleMapper ruleMapper = mock(ScheduleRuleMapper.class);
    private final ScheduleRuleSupport ruleSupport =
        new ScheduleRuleSupport(ruleMapper, classMapper, teacherMapper, timeSlotMapper, new ObjectMapper());
    private final ScheduleLessonServiceImpl service = new ScheduleLessonServiceImpl(
        lessonMapper,
        teacherRelMapper,
        classMapper,
        teacherMapper,
        classroomMapper,
        timeSlotMapper,
        ruleSupport
    );

    @BeforeEach
    void authenticateOperator() {
        // 课次接口的机构范围取自登录态（ScheduleInstitutionGuard），单测要先把它造出来
        authenticate(1L, 1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createLessonWritesTeacherRelations() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), any())).thenReturn(0L);
        doAnswer(invocation -> {
            ScheduleLessonEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        }).when(lessonMapper).insert(any(ScheduleLessonEntity.class));
        ScheduleTeacherRelEntity relation = new ScheduleTeacherRelEntity();
        relation.setLessonId(900L);
        relation.setTeacherId(400L);
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(relation));

        assertEquals(List.of(400L), service.createLesson(request()).teacherIds());

        verify(teacherRelMapper).physicalDeleteByLessonId(900L);
        verify(teacherRelMapper).insert(any(ScheduleTeacherRelEntity.class));
    }

    @Test
    void createLessonRejectsClassConflict() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.createLesson(request()));
    }

    @Test
    void createLessonRejectsForbiddenRuleCell() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), any())).thenReturn(0L);
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(forbiddenRule("教师没空")));

        BusinessException error = assertThrows(BusinessException.class, () -> service.createLesson(request()));

        assertTrue(error.getMessage().contains("禁排"));
        assertTrue(error.getMessage().contains("班级「测试班级」"), error.getMessage());
        assertTrue(error.getMessage().contains("教师没空"));
        verify(lessonMapper, never()).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void lessonWritesReportUniqueKeyRaceAsChineseConflict() {
        // 兜底约束是 V72 的 class_slot_guard：撞键说明另一个会话已把同一格子占下，不能把裸 SQL 文案透给客户
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), any())).thenReturn(0L);
        doThrow(new DuplicateKeyException(
            "Duplicate entry '100#2026-06-15#500' for key 'schedule_lesson.uk_schedule_lesson_class_slot'"))
            .when(lessonMapper).insert(any(ScheduleLessonEntity.class));

        BusinessException createError = assertThrows(BusinessException.class, () -> service.createLesson(request()));
        assertEquals(ResultCode.BAD_REQUEST, createError.getResultCode());
        assertTrue(createError.getMessage().contains("并发写入"), createError.getMessage());

        doThrow(new DuplicateKeyException(
            "Duplicate entry '200#2026-06-15#500' for key 'schedule_lesson.uk_schedule_lesson_classroom_slot'"))
            .when(lessonMapper).updateById(any(ScheduleLessonEntity.class));
        ScheduleLessonEntity existing = new ScheduleLessonEntity();
        existing.setId(900L);
        existing.setInstitutionId(1L);
        when(lessonMapper.selectById(900L)).thenReturn(existing);
        BusinessException updateError = assertThrows(BusinessException.class, () -> service.updateLesson(900L, request()));
        assertTrue(updateError.getMessage().contains("并发写入"), updateError.getMessage());
    }

    private ScheduleRuleEntity forbiddenRule(String remark) {
        ScheduleRuleEntity rule = new ScheduleRuleEntity();
        rule.setInstitutionId(1L);
        rule.setRuleType("forbidden");
        rule.setTargetType("class");
        rule.setTargetId(100L);
        rule.setSlotsJson("[{\"weekday\":1,\"timeSlotId\":500}]");
        rule.setStatus("active");
        rule.setRemark(remark);
        return rule;
    }

    @Test
    void createCancelledLessonSkipsConflictChecks() {
        stubValidMaterial();
        doAnswer(invocation -> {
            ScheduleLessonEntity entity = invocation.getArgument(0);
            entity.setId(901L);
            return 1;
        }).when(lessonMapper).insert(any(ScheduleLessonEntity.class));
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "停课",
            "cancelled",
            null,
            null,
            List.of(400L),
            "天气原因停课"
        ));

        verify(lessonMapper).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void updateMakeupLessonChecksConflictsWithExcludeId() {
        stubValidMaterial();
        ScheduleLessonEntity existing = new ScheduleLessonEntity();
        existing.setId(900L);
        existing.setInstitutionId(1L);
        when(lessonMapper.selectById(900L)).thenReturn(existing);
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), eq(900L))).thenReturn(0L);
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.updateLesson(900L, new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "补课",
            "makeup",
            null,
            null,
            List.of(400L),
            "周末补课"
        ));

        verify(teacherRelMapper).countTeacherConflicts(400L, LocalDate.of(2026, 6, 15), 500L, 900L);
    }

    @Test
    void updateLessonMarksLessonCompletedWithoutConflictChecks() {
        stubValidMaterial();
        ScheduleLessonEntity existing = new ScheduleLessonEntity();
        existing.setId(900L);
        existing.setInstitutionId(1L);
        when(lessonMapper.selectById(900L)).thenReturn(existing);
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.updateLesson(900L, new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "结课",
            "completed",
            null,
            null,
            List.of(400L),
            null
        ));

        ArgumentCaptor<ScheduleLessonEntity> captor = ArgumentCaptor.forClass(ScheduleLessonEntity.class);
        verify(lessonMapper).updateById(captor.capture());
        assertEquals("completed", captor.getValue().getLessonStatus());
        verify(teacherRelMapper, never()).countTeacherConflicts(any(), any(), any(), any());
    }

    @Test
    void createLessonRejectsUnsupportedStatus() {
        stubValidMaterial();

        assertThrows(BusinessException.class, () -> service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "异常状态",
            "paused",
            null,
            null,
            List.of(400L),
            "invalid"
        )));
    }

    private void stubValidMaterial() {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(100L);
        teachingClass.setInstitutionId(1L);
        teachingClass.setCampusId(2L);
        teachingClass.setTermId(3L);
        teachingClass.setClassName("测试班级");
        when(classMapper.selectById(100L)).thenReturn(teachingClass);
        // 禁排提示语要带出班级名称，名称是按 selectList 批量取的（ScheduleRuleSupport#targetNames）
        when(classMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(teachingClass));

        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(200L);
        classroom.setCampusId(2L);
        when(classroomMapper.selectById(200L)).thenReturn(classroom);

        TimeSlotEntity timeSlot = new TimeSlotEntity();
        timeSlot.setId(500L);
        timeSlot.setInstitutionId(1L);
        timeSlot.setCampusId(2L);
        timeSlot.setWeekday(1);
        timeSlot.setStartTime(LocalTime.of(9, 0));
        timeSlot.setEndTime(LocalTime.of(10, 0));
        when(timeSlotMapper.selectById(500L)).thenReturn(timeSlot);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(400L);
        teacher.setInstitutionId(1L);
        teacher.setCampusId(2L);
        when(teacherMapper.selectById(400L)).thenReturn(teacher);
    }

    private ScheduleLessonRequest request() {
        return new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "Math",
            "scheduled",
            null,
            null,
            List.of(400L),
            "test"
        );
    }

    @Test
    void createMakeupLessonRequiresCancelledRelatedLesson() {
        stubValidMaterial();
        ScheduleLessonEntity relatedLesson = new ScheduleLessonEntity();
        relatedLesson.setId(880L);
        relatedLesson.setInstitutionId(1L);
        relatedLesson.setCampusId(2L);
        relatedLesson.setTermId(3L);
        relatedLesson.setClassId(100L);
        relatedLesson.setLessonStatus("cancelled");
        when(lessonMapper.selectById(880L)).thenReturn(relatedLesson);
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), any())).thenReturn(0L);
        doAnswer(invocation -> {
            ScheduleLessonEntity entity = invocation.getArgument(0);
            entity.setId(902L);
            return 1;
        }).when(lessonMapper).insert(any(ScheduleLessonEntity.class));
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var lesson = service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "补课",
            "makeup",
            "cancelled_makeup",
            880L,
            List.of(400L),
            "关联停课补课"
        ));

        assertEquals("cancelled_makeup", lesson.changeType());
        assertEquals(880L, lesson.relatedLessonId());
    }

    @Test
    void createMakeupLessonRejectsNonCancelledRelatedLesson() {
        stubValidMaterial();
        ScheduleLessonEntity relatedLesson = new ScheduleLessonEntity();
        relatedLesson.setId(881L);
        relatedLesson.setInstitutionId(1L);
        relatedLesson.setCampusId(2L);
        relatedLesson.setTermId(3L);
        relatedLesson.setClassId(100L);
        relatedLesson.setLessonStatus("scheduled");
        when(lessonMapper.selectById(881L)).thenReturn(relatedLesson);

        assertThrows(BusinessException.class, () -> service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "补课",
            "makeup",
            "cancelled_makeup",
            881L,
            List.of(400L),
            "非法来源"
        )));
    }

    @Test
    void createMakeupLessonRejectsRelatedLessonWithoutCancelledMakeupChangeType() {
        stubValidMaterial();

        assertThrows(BusinessException.class, () -> service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "补课",
            "makeup",
            "manual_adjust",
            881L,
            List.of(400L),
            "非法变更类型"
        )));
    }

    @Test
    void listLessonsMarksCancelledLessonWithoutMakeupAsPendingMakeup() {
        ScheduleLessonEntity cancelledLesson = new ScheduleLessonEntity();
        cancelledLesson.setId(910L);
        cancelledLesson.setInstitutionId(1L);
        cancelledLesson.setCampusId(2L);
        cancelledLesson.setTermId(3L);
        cancelledLesson.setClassId(100L);
        cancelledLesson.setClassroomId(200L);
        cancelledLesson.setTimeSlotId(500L);
        cancelledLesson.setLessonDate(LocalDate.of(2026, 6, 15));
        cancelledLesson.setLessonWeekday(1);
        cancelledLesson.setStartTime(LocalTime.of(9, 0));
        cancelledLesson.setEndTime(LocalTime.of(10, 0));
        cancelledLesson.setLessonTitle("停课");
        cancelledLesson.setLessonStatus("cancelled");

        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cancelledLesson));
        when(lessonMapper.selectLinkedCancelledLessonIds(List.of(910L))).thenReturn(List.of());
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var lessons = service.listLessons(1L, 2L, 100L, LocalDate.of(2026, 6, 15), false);

        assertEquals(1, lessons.size());
        assertTrue(Boolean.TRUE.equals(lessons.getFirst().pendingMakeup()));
    }

    @Test
    void listLessonsCanFilterPendingMakeupOnly() {
        ScheduleLessonEntity cancelledPending = new ScheduleLessonEntity();
        cancelledPending.setId(910L);
        cancelledPending.setInstitutionId(1L);
        cancelledPending.setCampusId(2L);
        cancelledPending.setTermId(3L);
        cancelledPending.setClassId(100L);
        cancelledPending.setClassroomId(200L);
        cancelledPending.setTimeSlotId(500L);
        cancelledPending.setLessonDate(LocalDate.of(2026, 6, 15));
        cancelledPending.setLessonWeekday(1);
        cancelledPending.setStartTime(LocalTime.of(9, 0));
        cancelledPending.setEndTime(LocalTime.of(10, 0));
        cancelledPending.setLessonTitle("待补停课");
        cancelledPending.setLessonStatus("cancelled");

        ScheduleLessonEntity cancelledLinked = new ScheduleLessonEntity();
        cancelledLinked.setId(911L);
        cancelledLinked.setInstitutionId(1L);
        cancelledLinked.setCampusId(2L);
        cancelledLinked.setTermId(3L);
        cancelledLinked.setClassId(100L);
        cancelledLinked.setClassroomId(200L);
        cancelledLinked.setTimeSlotId(501L);
        cancelledLinked.setLessonDate(LocalDate.of(2026, 6, 16));
        cancelledLinked.setLessonWeekday(2);
        cancelledLinked.setStartTime(LocalTime.of(10, 0));
        cancelledLinked.setEndTime(LocalTime.of(11, 0));
        cancelledLinked.setLessonTitle("已补停课");
        cancelledLinked.setLessonStatus("cancelled");

        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cancelledPending, cancelledLinked));
        when(lessonMapper.selectLinkedCancelledLessonIds(List.of(910L, 911L))).thenReturn(List.of(911L));
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var lessons = service.listLessons(1L, 2L, 100L, null, true);

        assertEquals(1, lessons.size());
        assertEquals(910L, lessons.getFirst().id());
        assertTrue(Boolean.TRUE.equals(lessons.getFirst().pendingMakeup()));
    }

    @Test
    void dayTimetableAppliesClassIdFilter() {
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.dayTimetable(1L, 2L, 100L, LocalDate.of(2026, 6, 15), false);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lessonMapper).selectList(captor.capture());
        LambdaQueryWrapper wrapper = captor.getValue();
        // MP 的条件参数在渲染 SQL 片段时才注册，需先触发渲染再断言
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(100L));
    }

    @Test
    void dayTimetableRequiresLessonDate() {
        assertThrows(BusinessException.class, () -> service.dayTimetable(1L, 2L, null, null, false));
    }

    @Test
    void weekTimetableAppliesClassIdAndDateRange() {
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.weekTimetable(1L, 2L, 100L, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14), false);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lessonMapper).selectList(captor.capture());
        LambdaQueryWrapper wrapper = captor.getValue();
        wrapper.getSqlSegment();
        var values = wrapper.getParamNameValuePairs().values();
        assertTrue(values.contains(100L));
        assertTrue(values.contains(LocalDate.of(2026, 6, 8)));
        assertTrue(values.contains(LocalDate.of(2026, 6, 14)));
    }

    @Test
    void weekTimetableRejectsInvalidRange() {
        assertThrows(BusinessException.class,
            () -> service.weekTimetable(1L, 2L, null, LocalDate.of(2026, 6, 14), LocalDate.of(2026, 6, 8), false));
    }

    @Test
    void listLessonsAlwaysFiltersByLoginInstitution() {
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.listLessons(null, null, null, null, null);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lessonMapper).selectList(captor.capture());
        LambdaQueryWrapper wrapper = captor.getValue();
        wrapper.getSqlSegment();
        // 客户端不传机构不等于不限机构：范围一律收到登录机构上
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
    }

    @Test
    void queriesAndWritesRejectOtherInstitution() {
        BusinessException listError = assertThrows(BusinessException.class,
            () -> service.listLessons(2L, null, null, null, null));
        assertEquals(ResultCode.FORBIDDEN, listError.getResultCode());

        ScheduleLessonPageQuery query = new ScheduleLessonPageQuery();
        query.setInstitutionId(2L);
        assertEquals(ResultCode.FORBIDDEN,
            assertThrows(BusinessException.class, () -> service.pageLessons(query)).getResultCode());
        assertEquals(ResultCode.FORBIDDEN,
            assertThrows(BusinessException.class, () -> service.pagePendingMakeupLessons(query)).getResultCode());

        assertEquals(ResultCode.FORBIDDEN, assertThrows(BusinessException.class,
            () -> service.createLesson(foreignRequest())).getResultCode());
        verify(lessonMapper, never()).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void getLessonRejectsLessonFromAnotherInstitution() {
        ScheduleLessonEntity foreign = new ScheduleLessonEntity();
        foreign.setId(900L);
        foreign.setInstitutionId(2L);
        foreign.setLessonStatus("scheduled");
        when(lessonMapper.selectById(900L)).thenReturn(foreign);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getLesson(900L));

        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        assertTrue(error.getMessage().contains("排课课次"));
    }

    private ScheduleLessonRequest foreignRequest() {
        ScheduleLessonRequest own = request();
        return new ScheduleLessonRequest(
            2L,
            own.campusId(),
            own.termId(),
            own.classId(),
            own.classroomId(),
            own.timeSlotId(),
            own.lessonDate(),
            own.lessonTitle(),
            own.lessonStatus(),
            own.changeType(),
            own.relatedLessonId(),
            own.teacherIds(),
            own.remark()
        );
    }

    /** 排课服务的机构范围来自登录态，单测要把 SecurityContext 里的用户造出来（参照 AnnouncementServiceImplTest） */
    private void authenticate(Long userId, Long institutionId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, institutionId, "admin",
            Set.of("ADMIN"), Set.of("education:read", "education:write"), false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }
}
