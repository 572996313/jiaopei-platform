package com.jiaopei.platform.system;

import com.jiaopei.platform.auth.security.RestAccessDeniedHandler;
import com.jiaopei.platform.auth.security.RestAuthenticationEntryPoint;
import com.jiaopei.platform.auth.security.StubAuthenticationFilter;
import com.jiaopei.platform.common.web.ApiResponseAdvice;
import com.jiaopei.platform.common.web.GlobalExceptionHandler;
import com.jiaopei.platform.common.web.TraceIdFilter;
import com.jiaopei.platform.config.SecurityConfig;
import com.jiaopei.platform.system.controller.PlatformBootstrapController;
import com.jiaopei.platform.system.module.AuthModuleDescriptor;
import com.jiaopei.platform.support.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlatformBootstrapController.class)
@Import({
    SecurityConfig.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    StubAuthenticationFilter.class,
    TraceIdFilter.class,
    ApiResponseAdvice.class,
    GlobalExceptionHandler.class,
    WebMvcSecurityTestConfig.class,
    AuthModuleDescriptor.class
})
@TestPropertySource(properties = {
    "jiaopei.security.public-paths[0]=/api/auth/**",
    "jiaopei.security.public-paths[1]=/api/system/bootstrap",
    "jiaopei.security.public-paths[2]=/api/health",
    "spring.application.name=jiaopei-platform-backend"
})
class PlatformBootstrapControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrapIsPublic() throws Exception {
        mockMvc.perform(get("/api/system/bootstrap"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.application").value("jiaopei-platform-backend"))
            .andExpect(jsonPath("$.data.modules[0].code").value("auth"));
    }
}
