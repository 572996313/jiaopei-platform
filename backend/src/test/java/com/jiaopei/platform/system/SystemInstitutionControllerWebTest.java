package com.jiaopei.platform.system;

import com.jiaopei.platform.auth.aspect.PermissionGuardAspect;
import com.jiaopei.platform.auth.security.RestAccessDeniedHandler;
import com.jiaopei.platform.auth.security.RestAuthenticationEntryPoint;
import com.jiaopei.platform.auth.security.StubAuthenticationFilter;
import com.jiaopei.platform.auth.service.PermissionService;
import com.jiaopei.platform.common.web.ApiResponseAdvice;
import com.jiaopei.platform.common.web.GlobalExceptionHandler;
import com.jiaopei.platform.common.web.TraceIdFilter;
import com.jiaopei.platform.config.SecurityConfig;
import com.jiaopei.platform.organization.dto.CurrentInstitutionUpdateRequest;
import com.jiaopei.platform.organization.dto.CurrentInstitutionView;
import com.jiaopei.platform.organization.service.InstitutionService;
import com.jiaopei.platform.support.WebMvcSecurityTestConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(com.jiaopei.platform.system.controller.SystemInstitutionController.class)
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
class SystemInstitutionControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstitutionService institutionService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void currentRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/system/institution/current"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void currentUsesAuthenticatedUserInstitution() throws Exception {
        given(permissionService.hasPermission(any(), eq("organization:read"))).willReturn(true);
        given(institutionService.getCurrentInstitution(1L)).willReturn(new CurrentInstitutionView(
            1L,
            "SYS-DEFAULT",
            "系统默认机构",
            "默认机构",
            "system",
            "13800000000",
            "system",
            "active",
            "Initial institution"
        ));

        mockMvc.perform(get("/api/system/institution/current")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.name").value("系统默认机构"))
            .andExpect(jsonPath("$.data.shortName").value("默认机构"));

        verify(institutionService).getCurrentInstitution(1L);
    }

    @Test
    void updateCurrentUsesAuthenticatedUserInstitution() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(institutionService.updateCurrentInstitution(eq(1L), any(CurrentInstitutionUpdateRequest.class)))
            .willReturn(new CurrentInstitutionView(
                1L,
                "SYS-DEFAULT",
                "新机构名称",
                "新简称",
                "张老师",
                "13900000000",
                "新地址",
                "active",
                "更新备注"
            ));

        mockMvc.perform(put("/api/system/institution/current")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "新机构名称",
                      "shortName": "新简称",
                      "contactName": "张老师",
                      "contactPhone": "13900000000",
                      "address": "新地址",
                      "status": "active",
                      "remark": "更新备注"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("新机构名称"))
            .andExpect(jsonPath("$.data.contactName").value("张老师"));

        verify(institutionService).updateCurrentInstitution(eq(1L), any(CurrentInstitutionUpdateRequest.class));
    }
}
