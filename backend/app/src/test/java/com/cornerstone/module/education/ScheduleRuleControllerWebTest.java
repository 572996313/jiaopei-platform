package com.cornerstone.module.education;

import com.cornerstone.core.auth.aspect.PermissionGuardAspect;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.auth.service.PermissionService;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.module.education.controller.ScheduleRuleController;
import com.cornerstone.module.education.dto.ScheduleRuleView;
import com.cornerstone.module.education.dto.ScheduleSlotCell;
import com.cornerstone.module.education.service.ScheduleRuleService;
import com.cornerstone.support.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleRuleController.class)
@Import({
    // 业务模块改由 AutoConfiguration 装配，@WebMvcTest 切片会关掉自动配置，控制器要显式 @Import。
    ScheduleRuleController.class,
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
class ScheduleRuleControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleRuleService scheduleRuleService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void pageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/education/schedule-rules/page"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void pageReturnsRecords() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(scheduleRuleService.pageRules(any())).willReturn(new PageResult<>(List.of(view()), 1, 1, 10));

        mockMvc.perform(get("/api/education/schedule-rules/page")
                .header("Authorization", "Bearer stub-access-token")
                .param("institutionId", "1")
                .param("ruleType", "forbidden"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].id").value(900))
            .andExpect(jsonPath("$.data.records[0].slots[0].weekday").value(2));
    }

    @Test
    void createReturnsView() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(scheduleRuleService.createRule(any())).willReturn(view());

        mockMvc.perform(post("/api/education/schedule-rules")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(RULE_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ruleType").value("forbidden"))
            .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void createWithoutCellFailsValidation() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);

        mockMvc.perform(post("/api/education/schedule-rules")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"institutionId": 1, "ruleType": "forbidden", "targetType": "class", "targetId": 100, "slots": []}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(scheduleRuleService, never()).createRule(any());
    }

    @Test
    void duplicatedCellRendersBadRequestMessage() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(scheduleRuleService.createRule(any())).willThrow(new BusinessException(ResultCode.BAD_REQUEST,
            "该对象已存在覆盖同一格子的启用规则：777（周二，类型 required）"));

        mockMvc.perform(post("/api/education/schedule-rules")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(RULE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("同一格子")));
    }

    @Test
    void deleteInvokesService() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);

        mockMvc.perform(delete("/api/education/schedule-rules/900")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());

        verify(scheduleRuleService).deleteRule(anyLong());
    }

    private ScheduleRuleView view() {
        return new ScheduleRuleView(900L, 1L, "forbidden", "class", 100L,
            List.of(new ScheduleSlotCell(2, 500L)), null, null, "active", "周三固定教研", null);
    }

    private static final String RULE_BODY = """
        {
          "institutionId": 1,
          "ruleType": "forbidden",
          "targetType": "class",
          "targetId": 100,
          "slots": [{"weekday": 2, "timeSlotId": 500}],
          "remark": "周三固定教研"
        }
        """;
}
