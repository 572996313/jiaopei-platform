package com.jiaopei.platform.organization;

import com.jiaopei.platform.auth.aspect.PermissionGuardAspect;
import com.jiaopei.platform.auth.security.RestAccessDeniedHandler;
import com.jiaopei.platform.auth.security.RestAuthenticationEntryPoint;
import com.jiaopei.platform.auth.security.StubAuthenticationFilter;
import com.jiaopei.platform.auth.service.PermissionService;
import com.jiaopei.platform.common.web.ApiResponseAdvice;
import com.jiaopei.platform.common.web.GlobalExceptionHandler;
import com.jiaopei.platform.common.web.TraceIdFilter;
import com.jiaopei.platform.config.SecurityConfig;
import com.jiaopei.platform.organization.controller.TimeSlotTemplateController;
import com.jiaopei.platform.organization.dto.TimeSlotApplyResponse;
import com.jiaopei.platform.organization.dto.TimeSlotTemplateView;
import com.jiaopei.platform.organization.service.TimeSlotTemplateService;
import com.jiaopei.platform.support.WebMvcSecurityTestConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimeSlotTemplateController.class)
@Import({
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
class TimeSlotTemplateControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TimeSlotTemplateService timeSlotTemplateService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/organizations/time-slot-templates"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listUsesAuthenticatedUserInstitution() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(timeSlotTemplateService.listTemplates(any(), any(), any())).willReturn(List.of(
            new TimeSlotTemplateView(10L, 1L, null, "标准工作日课表", null, "active", null,
                LocalDateTime.now(), LocalDateTime.now())));

        mockMvc.perform(get("/api/organizations/time-slot-templates")
                .param("institutionId", "INST-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].templateName").value("标准工作日课表"));

        // 控制器把登录用户机构(orgId=1)作为租户参数传入,由服务端做机构比对
        verify(timeSlotTemplateService).listTemplates(eq(1L), eq(1L), any());
    }

    @Test
    void applyAcceptsGridPayload() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(timeSlotTemplateService.applyTemplate(any(), any(), any())).willReturn(
            new TimeSlotApplyResponse(5, 0, 0, 0, List.of()));

        mockMvc.perform(post("/api/organizations/time-slot-templates/10/apply")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "config": {"lessonDurationMinutes": 45, "weekdays": [1, 2, 3, 4, 5]},
                      "slots": [
                        {"weekday": 1, "slotCode": "AM1", "slotName": "上1", "startTime": "07:55:00", "endTime": "08:40:00", "slotType": "teaching", "sortNo": 101}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createdCount").value(5));

        verify(timeSlotTemplateService).applyTemplate(eq(1L), eq(10L), any());
    }

    @Test
    void applyRejectsEmptySlots() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);

        mockMvc.perform(post("/api/organizations/time-slot-templates/10/apply")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"slots": []}
                    """))
            .andExpect(status().isBadRequest());
    }
}
