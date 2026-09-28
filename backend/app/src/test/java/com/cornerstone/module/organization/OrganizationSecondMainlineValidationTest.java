package com.cornerstone.module.organization;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.dto.ClassroomCreateRequest;
import com.cornerstone.module.organization.dto.TermCreateRequest;
import com.cornerstone.module.organization.dto.TimeSlotCreateRequest;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.organization.service.impl.ClassroomServiceImpl;
import com.cornerstone.module.organization.service.impl.TermServiceImpl;
import com.cornerstone.module.organization.service.support.DictValueValidator;

import com.cornerstone.module.organization.service.impl.TimeSlotServiceImpl;
import com.cornerstone.support.CodeRuleTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationSecondMainlineValidationTest {

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，教室列表的校区收窄 wrapper 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ClassroomEntity.class);
        TableInfoHelper.initTableInfo(assistant, CampusEntity.class);
    }

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private CampusMapper campusMapper;

    @Mock
    private TermMapper termMapper;

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private TimeSlotMapper timeSlotMapper;

    @Test
    void createClassroomRejectsDuplicateCodeInCampus() {
        ClassroomServiceImpl service = new ClassroomServiceImpl(classroomMapper, campusMapper, new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(campusMapper.selectById(1L)).thenReturn(new CampusEntity());
        when(classroomMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createClassroom(classroomRequest("ROOM001", "一号教室")));

        verify(classroomMapper, never()).insert(any(ClassroomEntity.class));
    }

    @Test
    void createClassroomRejectsNegativeArea() {
        ClassroomServiceImpl service = new ClassroomServiceImpl(classroomMapper, campusMapper, new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(campusMapper.selectById(1L)).thenReturn(new CampusEntity());
        when(classroomMapper.selectCount(any())).thenReturn(0L, 0L);

        ClassroomCreateRequest request = new ClassroomCreateRequest(
            1L,
            "ROOM002",
            "二号教室",
            null,
            null,
            30,
            BigDecimal.valueOf(-1),
            null,
            null,
            null,
            null
        );
        assertThrows(BusinessException.class, () -> service.createClassroom(request));

        verify(classroomMapper, never()).insert(any(ClassroomEntity.class));
    }

    @Test
    void createTermRejectsDuplicateNameInInstitution() {
        TermServiceImpl service = new TermServiceImpl(termMapper, institutionMapper, new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(termMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThrows(BusinessException.class, () -> service.createTerm(termRequest(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 1))));

        verify(termMapper, never()).insert(any(TermEntity.class));
    }

    @Test
    void createTermRejectsInvalidDateRange() {
        TermServiceImpl service = new TermServiceImpl(termMapper, institutionMapper, new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());

        assertThrows(BusinessException.class, () -> service.createTerm(termRequest(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 7, 1))));

        verify(termMapper, never()).insert(any(TermEntity.class));
    }

    @Test
    void createTimeSlotRejectsDurationMismatch() {
        TimeSlotServiceImpl service = new TimeSlotServiceImpl(timeSlotMapper, institutionMapper, campusMapper,
            new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());

        TimeSlotCreateRequest request = timeSlotRequest(45);
        assertThrows(BusinessException.class, () -> service.createTimeSlot(1L, request));

        verify(timeSlotMapper, never()).insert(any(TimeSlotEntity.class));
    }

    @Test
    void createTimeSlotRejectsDuplicateSlotCodeInInstitution() {
        TimeSlotServiceImpl service = new TimeSlotServiceImpl(timeSlotMapper, institutionMapper, campusMapper,
            new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(timeSlotMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createTimeSlot(1L, timeSlotRequest(60)));

        verify(timeSlotMapper, never()).insert(any(TimeSlotEntity.class));
    }

    /** 传别人的校区 id 就能探到它的节次安排，所以列表侧同样要校验归属 */
    @Test
    void listTimeSlotsRejectsCampusOfAnotherInstitution() {
        TimeSlotServiceImpl service = new TimeSlotServiceImpl(timeSlotMapper, institutionMapper, campusMapper,
            new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        CampusEntity foreignCampus = new CampusEntity();
        foreignCampus.setInstitutionId(2L);
        when(campusMapper.selectById(99L)).thenReturn(foreignCampus);

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.listTimeSlots(1L, 1L, 99L));

        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        verify(timeSlotMapper, never()).selectList(any());
    }

    @Test
    void listTimeSlotsRejectsUnknownCampus() {
        TimeSlotServiceImpl service = new TimeSlotServiceImpl(timeSlotMapper, institutionMapper, campusMapper,
            new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(campusMapper.selectById(99L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.listTimeSlots(1L, 1L, 99L));

        assertEquals(ResultCode.NOT_FOUND, error.getResultCode());
        verify(timeSlotMapper, never()).selectList(any());
    }

    /** 全站范围「全部校区」会走到这条路：只能看本机构校区里的教室 */
    @Test
    void listClassroomsWithoutCampusStaysInsideOwnInstitution() {
        ClassroomServiceImpl service = new ClassroomServiceImpl(classroomMapper, campusMapper,
            new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(campusMapper.selectList(any())).thenReturn(List.of(campus(1L), campus(2L)));
        when(classroomMapper.selectList(any())).thenReturn(List.of());

        service.listClassrooms(1L, null);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classroomMapper).selectList(captor.capture());
        LambdaQueryWrapper wrapper = captor.getValue();
        // MP 的条件参数在渲染 SQL 片段时才注册，需先触发渲染再断言
        wrapper.getSqlSegment();
        var values = wrapper.getParamNameValuePairs().values();
        assertTrue(values.contains(1L) && values.contains(2L),
            "不传校区时必须按本机构校区集合收窄，实际参数：" + values);
    }

    /** 拿别家的校区 id 来探它的教室，直接拒绝 */
    @Test
    void listClassroomsRejectsCampusOfAnotherInstitution() {
        ClassroomServiceImpl service = new ClassroomServiceImpl(classroomMapper, campusMapper,
            new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(campusMapper.selectList(any())).thenReturn(List.of(campus(1L)));

        BusinessException error =
            assertThrows(BusinessException.class, () -> service.listClassrooms(1L, 999L));

        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        verify(classroomMapper, never()).selectList(any());
    }

    /** 本机构还没建校区就没有教室可列，也不该去查全库 */
    @Test
    void listClassroomsSkipsQueryWhenInstitutionHasNoCampus() {
        ClassroomServiceImpl service = new ClassroomServiceImpl(classroomMapper, campusMapper,
            new DictValueValidator(mock(SystemDictService.class)), CodeRuleTestSupport.unconfiguredRules());
        when(campusMapper.selectList(any())).thenReturn(List.of());

        assertEquals(0, service.listClassrooms(1L, null).size());

        verify(classroomMapper, never()).selectList(any());
    }

    private CampusEntity campus(Long id) {
        CampusEntity campus = new CampusEntity();
        campus.setId(id);
        return campus;
    }

    private ClassroomCreateRequest classroomRequest(String code, String name) {
        return new ClassroomCreateRequest(
            1L,
            code,
            name,
            null,
            null,
            30,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null
        );
    }

    private TermCreateRequest termRequest(LocalDate startDate, LocalDate endDate) {
        return new TermCreateRequest(
            1L,
            "TERM2026S",
            "2026 春季班",
            "2026",
            null,
            startDate,
            endDate,
            null,
            null,
            null,
            null,
            null
        );
    }

    private TimeSlotCreateRequest timeSlotRequest(Integer durationMinutes) {
        return new TimeSlotCreateRequest(
            1L,
            null,
            "SLOT001",
            "周一晚间",
            1,
            LocalTime.of(19, 0),
            LocalTime.of(20, 0),
            durationMinutes,
            null,
            null,
            null,
            null,
            null
        );
    }
}
