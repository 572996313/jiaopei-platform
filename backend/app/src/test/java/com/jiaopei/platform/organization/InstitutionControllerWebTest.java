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
import com.jiaopei.platform.tenant.controller.InstitutionController;
import com.jiaopei.platform.tenant.domain.InstitutionEntity;
import com.jiaopei.platform.tenant.service.InstitutionService;
import com.jiaopei.platform.support.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstitutionController.class)
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
class InstitutionControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstitutionService institutionService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/organizations/institutions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listReturnsDataWithStubToken() throws Exception {
        InstitutionEntity entity = new InstitutionEntity();
        entity.setId(1L);
        entity.setOrgCode("ORG001");
        entity.setOrgName("测试机构");
        given(permissionService.hasPermission(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("organization:read")))
            .willReturn(true);
        given(institutionService.listInstitutions()).willReturn(List.of(entity));

        mockMvc.perform(get("/api/organizations/institutions")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].orgCode").value("ORG001"))
            .andExpect(jsonPath("$.data[0].orgName").value("测试机构"));
    }
}
