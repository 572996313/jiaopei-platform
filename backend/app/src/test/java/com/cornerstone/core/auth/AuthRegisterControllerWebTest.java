package com.cornerstone.core.auth;

import com.cornerstone.core.auth.controller.AuthRegisterController;
import com.cornerstone.core.auth.dto.InvitationPrecheckView;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.support.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthRegisterController.class)
@Import({
    SecurityConfig.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    StubAuthenticationFilter.class,
    TraceIdFilter.class,
    ApiResponseAdvice.class,
    GlobalExceptionHandler.class,
    WebMvcSecurityTestConfig.class
})
@TestPropertySource(properties = {
    "cornerstone.security.public-paths[0]=/api/auth/**",
    "cornerstone.security.public-paths[1]=/api/health"
})
class AuthRegisterControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthInvitationService authInvitationService;

    @Test
    void precheckIsPublicAndReturnsInstitutionInfo() throws Exception {
        when(authInvitationService.precheck(anyString())).thenReturn(new InvitationPrecheckView(
            true, "INVITE", "示范机构", "STAFF", "职员", null, null));

        mockMvc.perform(get("/api/auth/register/precheck").param("code", "INVABCDEFGHJKLMN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.valid").value(true))
            .andExpect(jsonPath("$.data.institutionName").value("示范机构"))
            .andExpect(jsonPath("$.data.roleName").value("职员"));
    }

    @Test
    void registerIssuesTokenWithoutAuthentication() throws Exception {
        when(authInvitationService.register(any())).thenReturn(new LoginResponse(
            77L, 1L, "zhangsan", "张三", List.of("STAFF"), List.of(), "Bearer", "jwt-token", false, "admin"));

        mockMvc.perform(post("/api/auth/register/invitation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "INVABCDEFGHJKLMN",
                      "username": "zhangsan",
                      "displayName": "张三",
                      "phone": "13800000000",
                      "password": "Passw0rd!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
            .andExpect(jsonPath("$.data.username").value("zhangsan"));
    }

    @Test
    void activateRejectsMissingCodeWithValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/register/activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "",
                      "password": "Passw0rd!"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }
}
