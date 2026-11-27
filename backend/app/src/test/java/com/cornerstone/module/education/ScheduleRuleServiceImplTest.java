package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ScheduleRuleEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.ScheduleRuleMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.schedule.dto.ScheduleRulePageQuery;
import com.cornerstone.module.education.schedule.dto.ScheduleRuleRequest;
import com.cornerstone.module.education.schedule.dto.ScheduleRuleView;
import com.cornerstone.module.education.schedule.dto.ScheduleSlotCell;
import com.cornerstone.module.education.schedule.service.impl.ScheduleRuleServiceImpl;
import com.cornerstone.module.education.schedule.support.ScheduleRuleSupport;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ScheduleRuleServiceImplTest {

    private static final ScheduleSlotCell MONDAY_SLOT = new ScheduleSlotCell(1, 500L);

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ScheduleRuleEntity.class);
    }

    private final ScheduleRuleMapper ruleMapper = mock(ScheduleRuleMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
    private final ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduleRuleSupport ruleSupport =
        new ScheduleRuleSupport(ruleMapper, classMapper, teacherMapper, timeSlotMapper, classroomMapper, objectMapper);
    private final ScheduleRuleServiceImpl service =
        new ScheduleRuleServiceImpl(ruleMapper, classMapper, teacherMapper, timeSlotMapper,
            classroomMapper, campusMapper, ruleSupport);

    @BeforeEach
    void authenticateOperator() {
        // 规则接口的机构范围取自登录态（ScheduleInstitutionGuard），单测要先把它造出来
        authenticate(1L, 1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRulePersistsNormalizedDefinitionAndDefaultsToActive() {
        stubClassTarget();
        stubMondaySlot();
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        stubInsertReturns(900L);

        ScheduleRuleView view = service.createRule(new ScheduleRuleRequest(
            1L, " Forbidden ", " Class ", 100L, List.of(MONDAY_SLOT),
            null, null, null, "周三固定教研"
        ));

        ArgumentCaptor<ScheduleRuleEntity> captor = ArgumentCaptor.forClass(ScheduleRuleEntity.class);
        verify(ruleMapper).insert(captor.capture());
        ScheduleRuleEntity saved = captor.getValue();
        assertEquals("forbidden", saved.getRuleType());
        assertEquals("class", saved.getTargetType());
        assertEquals("active", saved.getStatus());
        assertEquals("[{\"weekday\":1,\"timeSlotId\":500}]", saved.getSlotsJson());
        assertEquals(500L, view.slots().getFirst().timeSlotId());
    }

    @Test
    void createRuleRejectsCellAlreadyCoveredByAnotherActiveRule() {
        stubClassTarget();
        stubMondaySlot();
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(existingRule(777L, "required", "[{\"weekday\":1,\"timeSlotId\":500}]")));

        // 同一格子先禁排再必排属于自相矛盾，两类规则要互斥而不是各说各话
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.createRule(request("forbidden", List.of(MONDAY_SLOT))));

        // 报错只说"星期 + 类型"让操作员去规则列表里定位，不报 19 位规则 ID、也不露 required 码值
        assertEquals("该对象周一已存在启用中的必排规则", error.getMessage());
        verify(ruleMapper, never()).insert(any(ScheduleRuleEntity.class));
    }

    @Test
    void createRuleAllowsCellThatOnlyInactiveRulesUse() {
        stubClassTarget();
        stubMondaySlot();
        // assertCellsFree 只查启用中的规则，停用规则让开格子
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        stubInsertReturns(901L);

        assertEquals(901L, service.createRule(request("forbidden", List.of(MONDAY_SLOT))).id());

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ruleMapper).selectList(captor.capture());
        LambdaQueryWrapper wrapper = captor.getValue();
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("active"));
    }

    @Test
    void createRuleRejectsUnknownRuleTypeAndTargetType() {
        assertThrows(BusinessException.class,
            () -> service.createRule(request("soft", List.of(MONDAY_SLOT))));

        stubClassTarget();
        assertThrows(BusinessException.class,
            () -> service.createRule(new ScheduleRuleRequest(1L, "forbidden", "student", 100L,
                List.of(MONDAY_SLOT), null, null, null, null)));
        verify(ruleMapper, never()).insert(any(ScheduleRuleEntity.class));
    }

    @Test
    void createRuleRejectsTargetFromAnotherInstitution() {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(100L);
        teachingClass.setInstitutionId(2L);
        teachingClass.setCampusId(10L);
        when(classMapper.selectById(100L)).thenReturn(teachingClass);

        assertThrows(BusinessException.class, () -> service.createRule(request("forbidden", List.of(MONDAY_SLOT))));
    }

    @Test
    void createRuleRejectsSlotWhoseWeekdayDiffersFromCell() {
        stubClassTarget();
        stubMondaySlot();
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        // 时段自带星期，格子必须与之一致，否则排课按 weekday 匹配永远命中不了
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.createRule(request("forbidden", List.of(new ScheduleSlotCell(3, 500L)))));
        assertTrue(error.getMessage().contains("星期"));
    }

    @Test
    void createRuleRejectsInvertedDateRange() {
        stubClassTarget();
        stubMondaySlot();
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.createRule(new ScheduleRuleRequest(
            1L, "forbidden", "class", 100L, List.of(MONDAY_SLOT),
            LocalDate.of(2026, 6, 14), LocalDate.of(2026, 6, 1), null, null
        )));
    }

    @Test
    void updateRuleKeepsOwnCellsAndRejectsForeignInstitution() {
        ScheduleRuleEntity existing = existingRule(900L, "forbidden", "[{\"weekday\":2,\"timeSlotId\":501}]");
        existing.setInstitutionId(1L);
        existing.setTargetType("class");
        existing.setTargetId(100L);
        when(ruleMapper.selectById(900L)).thenReturn(existing);
        stubClassTarget();
        TimeSlotEntity tuesdaySlot = new TimeSlotEntity();
        tuesdaySlot.setId(501L);
        tuesdaySlot.setInstitutionId(1L);
        tuesdaySlot.setCampusId(10L);
        tuesdaySlot.setWeekday(2);
        tuesdaySlot.setSlotName("上2");
        when(timeSlotMapper.selectById(501L)).thenReturn(tuesdaySlot);
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.updateRule(900L, new ScheduleRuleRequest(1L, "forbidden", "class", 100L,
            List.of(new ScheduleSlotCell(2, 501L)), null, null, "inactive", "改期"));

        ArgumentCaptor<ScheduleRuleEntity> captor = ArgumentCaptor.forClass(ScheduleRuleEntity.class);
        verify(ruleMapper).updateById(captor.capture());
        assertEquals("inactive", captor.getValue().getStatus());

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.updateRule(900L, new ScheduleRuleRequest(2L, "forbidden", "class", 100L,
                List.of(new ScheduleSlotCell(2, 501L)), null, null, null, null)));
        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        assertTrue(error.getMessage().contains("排课规则"));
    }

    @Test
    void pageRulesAlwaysFilterByLoginInstitution() {
        when(ruleMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        service.pageRules(new ScheduleRulePageQuery());

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ruleMapper).selectPage(any(), captor.capture());
        LambdaQueryWrapper wrapper = captor.getValue();
        wrapper.getSqlSegment();
        // 不传机构不再等于全库可见
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
    }

    @Test
    void ruleQueriesAndReadsRejectOtherInstitution() {
        ScheduleRulePageQuery foreignQuery = new ScheduleRulePageQuery();
        foreignQuery.setInstitutionId(2L);
        assertEquals(ResultCode.FORBIDDEN,
            assertThrows(BusinessException.class, () -> service.pageRules(foreignQuery)).getResultCode());

        ScheduleRuleEntity foreign = existingRule(903L, "forbidden", "[{\"weekday\":1,\"timeSlotId\":500}]");
        foreign.setInstitutionId(2L);
        when(ruleMapper.selectById(903L)).thenReturn(foreign);
        assertEquals(ResultCode.FORBIDDEN,
            assertThrows(BusinessException.class, () -> service.getRule(903L)).getResultCode());
        assertEquals(ResultCode.FORBIDDEN,
            assertThrows(BusinessException.class, () -> service.deleteRule(903L)).getResultCode());
        verify(ruleMapper, never()).deleteById(903L);
    }

    @Test
    void teacherTargetUsesTeacherInstitutionAndCampus() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(400L);
        teacher.setInstitutionId(1L);
        teacher.setCampusId(10L);
        when(teacherMapper.selectById(400L)).thenReturn(teacher);
        stubMondaySlot();
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        stubInsertReturns(902L);

        assertEquals("teacher", service.createRule(new ScheduleRuleRequest(1L, "required", "teacher", 400L,
            List.of(MONDAY_SLOT), null, null, null, null)).targetType());
    }

    @Test
    void deleteRuleRejectsMissingRule() {
        when(ruleMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.deleteRule(999L));
        verify(ruleMapper, never()).deleteById(999L);
    }

    @Test
    void classroomTargetResolvesInstitutionThroughItsCampus() {
        stubClassroomTarget(1L);
        stubMondaySlot();
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        stubInsertReturns(904L);

        assertEquals("classroom", service.createRule(new ScheduleRuleRequest(1L, "forbidden", "classroom", 200L,
            List.of(MONDAY_SLOT), null, null, null, null)).targetType());
    }

    @Test
    void classroomTargetRejectsRoomOfAnotherInstitution() {
        // 教室表上没有机构列，归属只能经校区认定；校区不是本机构时这条规则不许建
        stubClassroomTarget(2L);
        stubMondaySlot();

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.createRule(new ScheduleRuleRequest(1L, "forbidden", "classroom", 200L,
                List.of(MONDAY_SLOT), null, null, null, null)));
        assertTrue(error.getMessage().contains("教室与规则的机构不一致"), error.getMessage());
        verify(ruleMapper, never()).insert(any(ScheduleRuleEntity.class));
    }

    private void stubClassroomTarget(Long campusInstitutionId) {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(200L);
        classroom.setCampusId(10L);
        classroom.setClassroomName("3号音乐教室");
        when(classroomMapper.selectById(200L)).thenReturn(classroom);
        CampusEntity campus = new CampusEntity();
        campus.setId(10L);
        campus.setInstitutionId(campusInstitutionId);
        when(campusMapper.selectById(10L)).thenReturn(campus);
    }

    private void stubInsertReturns(long id) {
        ScheduleRuleEntity[] inserted = new ScheduleRuleEntity[1];
        doAnswer(invocation -> {
            inserted[0] = invocation.getArgument(0);
            inserted[0].setId(id);
            return 1;
        }).when(ruleMapper).insert(any(ScheduleRuleEntity.class));
        when(ruleMapper.selectById(id)).thenAnswer(invocation -> inserted[0]);
    }

    private void stubClassTarget() {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(100L);
        teachingClass.setInstitutionId(1L);
        teachingClass.setCampusId(10L);
        when(classMapper.selectById(100L)).thenReturn(teachingClass);
    }

    private void stubMondaySlot() {
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

    private ScheduleRuleEntity existingRule(Long id, String ruleType, String slotsJson) {
        ScheduleRuleEntity entity = new ScheduleRuleEntity();
        entity.setId(id);
        entity.setInstitutionId(1L);
        entity.setRuleType(ruleType);
        entity.setTargetType("class");
        entity.setTargetId(100L);
        entity.setSlotsJson(slotsJson);
        entity.setStatus("active");
        return entity;
    }

    private ScheduleRuleRequest request(String ruleType, List<ScheduleSlotCell> slots) {
        return new ScheduleRuleRequest(1L, ruleType, "class", 100L, slots, null, null, null, null);
    }

    /** 排课服务的机构范围来自登录态，单测要把 SecurityContext 里的用户造出来（参照 AnnouncementServiceImplTest） */
    private void authenticate(Long userId, Long institutionId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, institutionId, "admin",
            Set.of("ADMIN"), Set.of("education:read", "education:schedule:write"), false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }
}
