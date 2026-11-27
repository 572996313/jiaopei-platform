package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.SchoolCalendarDayEntity;
import com.cornerstone.module.education.mapper.SchoolCalendarDayMapper;
import com.cornerstone.module.education.schedule.dto.SchoolCalendarRequest;
import com.cornerstone.module.education.schedule.service.impl.SchoolCalendarServiceImpl;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

/**
 * 校区日历（停排日）：读取范围只认「机构级(0) + 本校区」，同一天两处都登记时以范围更窄的校区级为准；
 * 撞唯一键要翻成中文，跨机构一律拒绝。全部是"只提示不拦"，所以这里没有冲突判定。
 */
class SchoolCalendarServiceImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    private static final Long CAMPUS_ID = 10L;

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            SchoolCalendarDayEntity.class);
    }

    private final SchoolCalendarDayMapper calendarMapper = mock(SchoolCalendarDayMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final SchoolCalendarServiceImpl service = new SchoolCalendarServiceImpl(calendarMapper, campusMapper);

    @BeforeEach
    void authenticateOperator() {
        // 校区日历的机构范围取自登录态（ScheduleInstitutionGuard），单测要先把它造出来
        AuthenticatedUser user = new AuthenticatedUser(1L, 1L, "admin",
            Set.of("ADMIN"), Set.of("education:read", "education:schedule:write"), false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listDaysWithCampusStillIncludesInstitutionWideRows() {
        when(calendarMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.listDays(1L, CAMPUS_ID, 2026);

        LambdaQueryWrapper<?> wrapper = captureListWrapper();
        // 0（全校区）必须一起列出来：漏了它，面板按校区筛选就看不见机构级记录，而排课时它照样在提示里
        assertTrue(wrapper.getParamNameValuePairs().containsValue(0L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(CAMPUS_ID));
        // 年份筛选按整年区间下推到 SQL，不是把 2026 当列值比
        assertTrue(wrapper.getParamNameValuePairs().containsValue(LocalDate.of(2026, 1, 1)));
    }

    @Test
    void closedDaysWithoutCampusReadsOnlyInstitutionWideRows() {
        when(calendarMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.closedDays(1L, null, DAY, DAY);

        LambdaQueryWrapper<?> wrapper = captureListWrapper();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(0L));
        assertFalse(wrapper.getParamNameValuePairs().containsValue(CAMPUS_ID));
    }

    @Test
    void campusLevelNoteWinsWhenTheSameDayIsRegisteredTwice() {
        // 真实查询按 campus_id 倒序，校区级排在机构级前面；这里照这个顺序喂给服务
        when(calendarMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            day(1L, CAMPUS_ID, DAY, "校区检修", "只停本课程"),
            day(1L, 0L, DAY, "国庆节", "全校放假")
        ));

        Map<LocalDate, String> notices = service.closedDays(1L, CAMPUS_ID, DAY, DAY);

        assertEquals(1, notices.size());
        assertTrue(notices.get(DAY).contains("本校区"), notices.get(DAY));
        assertTrue(notices.get(DAY).contains("只停本课程"));
    }

    @Test
    void institutionWideNoticeSaysSchoolWideAndHintOnly() {
        when(calendarMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(day(1L, 0L, DAY, "国庆节", "全校放假")));

        String notice = service.closedDayNotice(1L, CAMPUS_ID, DAY);

        assertEquals("停排日「国庆节」(全校区，只提示不拦)：全校放假", notice);
    }

    @Test
    void noNoticeWhenTheDayIsNotRegistered() {
        when(calendarMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertNull(service.closedDayNotice(1L, CAMPUS_ID, DAY));
    }

    @Test
    void blankCampusIsStoredAsInstitutionWideAndNameIsTrimmed() {
        stubInsertReturns(900L);

        service.createDay(new SchoolCalendarRequest(1L, null, DAY, "  国庆节  ", null));

        ArgumentCaptor<SchoolCalendarDayEntity> captor = ArgumentCaptor.forClass(SchoolCalendarDayEntity.class);
        verify(calendarMapper).insert(captor.capture());
        // 留空 = 全校区，落库是 0 而不是 NULL：NULL 在唯一键里互不相等，同一天能插进无数条
        assertEquals(0L, captor.getValue().getCampusId());
        assertEquals("国庆节", captor.getValue().getDayName());
        verify(campusMapper, never()).selectById(any());
    }

    @Test
    void createDayRejectsCampusOfAnotherInstitution() {
        CampusEntity campus = new CampusEntity();
        campus.setId(CAMPUS_ID);
        campus.setInstitutionId(2L);
        when(campusMapper.selectById(CAMPUS_ID)).thenReturn(campus);

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.createDay(new SchoolCalendarRequest(1L, CAMPUS_ID, DAY, "检修", null)));

        assertTrue(error.getMessage().contains("校区不存在或不属于该机构"), error.getMessage());
        verify(calendarMapper, never()).insert(any(SchoolCalendarDayEntity.class));
    }

    @Test
    void duplicateDayBecomesChineseConflictInsteadOfRawSqlError() {
        doThrow(new DuplicateKeyException(
            "Duplicate entry '1#0#2026-10-01#0' for key 'jp_school_calendar_day.uk_jp_school_calendar_day'"))
            .when(calendarMapper).insert(any(SchoolCalendarDayEntity.class));

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.createDay(new SchoolCalendarRequest(1L, null, DAY, "国庆节", null)));

        assertEquals(ResultCode.BAD_REQUEST, error.getResultCode());
        assertTrue(error.getMessage().contains("该日期已登记停排"), error.getMessage());
    }

    @Test
    void deleteDayRejectsRowOfAnotherInstitution() {
        when(calendarMapper.selectById(900L)).thenReturn(day(2L, 0L, DAY, "别人的日历", null));

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteDay(900L));

        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        assertTrue(error.getMessage().contains("校区日历"));
        // 物理删除只在归属通过后执行，否则等于给别家机构删数据开了个口子
        verify(calendarMapper, never()).deletePhysicallyById(900L);
    }

    @Test
    void queriesRejectForeignInstitutionParam() {
        // 不传机构按登录机构查，传了别人的直接拒绝——少传一个参数不等于全库可见
        BusinessException error = assertThrows(BusinessException.class, () -> service.listDays(2L, null, 2026));
        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        verify(calendarMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    // ------------------------------------------------------------------ 夹具

    private LambdaQueryWrapper<?> captureListWrapper() {
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(calendarMapper).selectList(captor.capture());
        LambdaQueryWrapper<?> wrapper = captor.getValue();
        wrapper.getSqlSegment();
        return wrapper;
    }

    private void stubInsertReturns(long id) {
        SchoolCalendarDayEntity[] inserted = new SchoolCalendarDayEntity[1];
        doAnswer(invocation -> {
            inserted[0] = invocation.getArgument(0);
            inserted[0].setId(id);
            return 1;
        }).when(calendarMapper).insert(any(SchoolCalendarDayEntity.class));
        when(calendarMapper.selectById(id)).thenAnswer(invocation -> inserted[0]);
    }

    private SchoolCalendarDayEntity day(Long institutionId, Long campusId, LocalDate dayDate,
                                        String dayName, String remark) {
        SchoolCalendarDayEntity entity = new SchoolCalendarDayEntity();
        entity.setId(900L);
        entity.setInstitutionId(institutionId);
        entity.setCampusId(campusId);
        entity.setDayDate(dayDate);
        entity.setDayName(dayName);
        entity.setRemark(remark);
        return entity;
    }
}
