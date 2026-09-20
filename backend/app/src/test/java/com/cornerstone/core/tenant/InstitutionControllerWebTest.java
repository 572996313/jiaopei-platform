package com.cornerstone.core.tenant;

import com.cornerstone.core.auth.aspect.PermissionGuardAspect;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.auth.service.PermissionService;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.core.tenant.controller.InstitutionController;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.service.InstitutionService;
import com.cornerstone.support.WebMvcSecurityTestConfig;
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
        given(permissionService.hasPermission(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("tenant:read")))
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
