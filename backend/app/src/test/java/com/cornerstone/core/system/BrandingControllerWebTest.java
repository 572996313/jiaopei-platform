package com.cornerstone.core.system;

import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.core.system.controller.BrandingController;
import com.cornerstone.support.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrandingController.class)
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
    "cornerstone.security.public-paths[1]=/api/system/branding",
    "cornerstone.security.public-paths[2]=/api/health"
})
class BrandingControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void brandingIsPublicAndComesFromSystemConfig() throws Exception {
        // 登录页要展示系统名称，所以这个接口必须免登录可读，且值来自 brand.* 而不是前端构建期常量
        mockMvc.perform(get("/api/system/branding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("测试平台"))
            .andExpect(jsonPath("$.data.tagline").value("测试用定位副标题"))
            .andExpect(jsonPath("$.data.loginFormTitle").value("进入测试平台"));
    }

    @Test
    void markIsDerivedFromBrandName() throws Exception {
        // 角标不单独配参数，跟着系统名称走，避免"改了名称、角标还是旧字"
        mockMvc.perform(get("/api/system/branding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mark").value("测"));
    }

    @Test
    void unconfiguredBrandReturnsEmptyString() throws Exception {
        // 未配置返回空串而不是回落某个默认名，由前端显示"去系统参数维护"的提示
        mockMvc.perform(get("/api/system/branding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.loginCopy").value(""));
    }
}
