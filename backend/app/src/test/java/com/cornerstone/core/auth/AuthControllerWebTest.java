package com.cornerstone.core.auth;

import com.cornerstone.core.auth.controller.AuthController;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.support.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
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
    "cornerstone.security.public-paths[1]=/api/system/bootstrap",
    "cornerstone.security.public-paths[2]=/api/health"
})
class AuthControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginReturnsStubToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "admin",
                      "password": "123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.accessToken").value("stub-access-token"))
            .andExpect(jsonPath("$.data.permissions[0]").value("organization:read"));
    }

    @Test
    void loginPageTipComesFromSystemConfigAndIsPublic() throws Exception {
        // 登录页提示语写进系统参数：底座不硬编码文案，未登录也要能取到
        mockMvc.perform(get("/api/auth/login-page"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.tip").value("默认管理员账号 admin"));
    }

    @Test
    void loginWrongPasswordKeepsOriginalMessage() throws Exception {
        // 回归：认证异常的原始消息必须透传给前端，不能被笼统的"未登录或登录已失效"掩盖
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "admin",
                      "password": "wrong-password"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }
}
