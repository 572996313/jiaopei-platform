package com.cornerstone.core.system;

import com.cornerstone.core.auth.aspect.PermissionGuardAspect;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.auth.service.PermissionService;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.support.WebMvcSecurityTestConfig;
import com.cornerstone.core.system.controller.SystemDictController;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.dto.DictOptionGroupView;
import com.cornerstone.core.system.service.SystemDictService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemDictController.class)
@Import({
    AopAutoConfiguration.class,
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
class SystemDictControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemDictService systemDictService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void optionsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/system/dicts/options").param("typeCodes", "room_type"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void optionsAccessibleWithoutDictManagePermission() throws Exception {
        DictItemView item = new DictItemView(10L, 2L, "normal", "标准教室", "normal", 10, "active", null);
        given(systemDictService.listOptions(List.of("room_type")))
            .willReturn(List.of(new DictOptionGroupView("room_type", "教室类型", List.of(item))));

        mockMvc.perform(get("/api/system/dicts/options")
                .param("typeCodes", "room_type")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].typeCode").value("room_type"))
            .andExpect(jsonPath("$.data[0].typeName").value("教室类型"))
            .andExpect(jsonPath("$.data[0].items[0].itemCode").value("normal"))
            .andExpect(jsonPath("$.data[0].items[0].itemName").value("标准教室"));
    }

    @Test
    void deleteTypeRequiresDictManagePermission() throws Exception {
        given(permissionService.hasPermission(any(), eq("system:dict:manage"))).willReturn(false);

        // PermissionDeniedException 走 BusinessException 处理器：HTTP 400 + 响应体 code=FORBIDDEN
        mockMvc.perform(delete("/api/system/dicts/types/2")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
