package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cornerstone.core.auth.aspect.PermissionGuardAspect;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.auth.service.PermissionService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.module.education.schedule.controller.ScheduleCompletenessController;
import com.cornerstone.module.education.schedule.dto.ScheduleCompletenessQuery;
import com.cornerstone.module.education.schedule.dto.ScheduleCompletenessResponse;
import com.cornerstone.module.education.schedule.service.ScheduleCompletenessService;
import com.cornerstone.support.WebMvcSecurityTestConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleCompletenessController.class)
@Import({
    // 业务模块改由 AutoConfiguration 装配，@WebMvcTest 切片会关掉自动配置，控制器要显式 @Import。
    ScheduleCompletenessController.class,
    SecurityConfig.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    StubAuthenticationFilter.class,
    TraceIdFilter.class,
    ApiResponseAdvice.class,
    GlobalExceptionHandler.class,
    PermissionGuardAspect.class,
    WebMvcSecurityTestConfig.class
})
class ScheduleCompletenessControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleCompletenessService scheduleCompletenessService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void reportRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/education/schedule-completeness"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void reportBindsDateParamsAndReturnsMatrix() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(scheduleCompletenessService.report(anyLong(), any())).willReturn(response());

        mockMvc.perform(get("/api/education/schedule-completeness")
                .header("Authorization", "Bearer stub-access-token")
                .param("institutionId", "1")
                .param("classId", "100")
                .param("dateFrom", "2026-06-01")
                .param("dateTo", "2026-06-14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weeks[0].weekStart").value("2026-06-01"))
            .andExpect(jsonPath("$.data.classes[0].cells[1].gaps[0].slotName").value("上1"))
            .andExpect(jsonPath("$.data.summary.generatedPercent").value(50));
    }

    @Test
    void queryCarriesParsedDates() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(scheduleCompletenessService.report(anyLong(), any())).willReturn(response());

        mockMvc.perform(get("/api/education/schedule-completeness")
            .header("Authorization", "Bearer stub-access-token")
            .param("dateFrom", "2026-06-03")
            .param("dateTo", "2026-06-04"));

        ArgumentCaptor<ScheduleCompletenessQuery> captor = ArgumentCaptor.forClass(ScheduleCompletenessQuery.class);
        verify(scheduleCompletenessService).report(anyLong(), captor.capture());
        assertEquals(LocalDate.of(2026, 6, 3), captor.getValue().getDateFrom());
        assertEquals(LocalDate.of(2026, 6, 4), captor.getValue().getDateTo());
    }

    @Test
    void tooWideRangeRendersBadRequestMessage() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(scheduleCompletenessService.report(anyLong(), any())).willThrow(
            new BusinessException(ResultCode.BAD_REQUEST, "统计区间不能超过 26 周"));

        mockMvc.perform(get("/api/education/schedule-completeness")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("26")));
    }

    private ScheduleCompletenessResponse response() {
        LocalDate weekFrom = LocalDate.of(2026, 6, 1);
        List<ScheduleCompletenessResponse.WeekColumn> weeks = List.of(
            new ScheduleCompletenessResponse.WeekColumn(1, weekFrom, weekFrom.plusDays(6)),
            new ScheduleCompletenessResponse.WeekColumn(2, weekFrom.plusWeeks(1), weekFrom.plusWeeks(1).plusDays(6)));
        ScheduleCompletenessResponse.WeekCell first = new ScheduleCompletenessResponse.WeekCell(true, 2, 2, 0, 1, 0, 0,
            List.of());
        ScheduleCompletenessResponse.WeekCell second = new ScheduleCompletenessResponse.WeekCell(true, 2, 0, 2, 0, 0, 0,
            List.of(new ScheduleCompletenessResponse.Gap(LocalDate.of(2026, 6, 8), 1, 500L, "上1")));
        ScheduleCompletenessResponse.ClassRow row = new ScheduleCompletenessResponse.ClassRow(100L, "测试班级",
            10L, 20L, 1, "plan_expand", null, null, null, null, LocalDate.of(2026, 6, 14), List.of(first, second),
            new ScheduleCompletenessResponse.RowSummary(4, 2, 1, 2, 0));
        return new ScheduleCompletenessResponse(weekFrom, LocalDate.of(2026, 6, 14), weeks, List.of(row),
            new ScheduleCompletenessResponse.Summary(1, 4, 2, 1, 2, 0, 50, 25));
    }
}
