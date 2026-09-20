package com.cornerstone.core.system.controller;

import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.ModuleSummaryView;
import com.cornerstone.core.system.dto.PlatformBootstrapView;
import com.cornerstone.core.system.module.ModuleDescriptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/bootstrap")
@Tag(name = "系统管理")
public class PlatformBootstrapController {

    private final ObjectProvider<ModuleDescriptor> moduleDescriptors;

    @Value("${spring.application.name}")
    private String applicationName;

    public PlatformBootstrapController(ObjectProvider<ModuleDescriptor> moduleDescriptors) {
        this.moduleDescriptors = moduleDescriptors;
    }

    @Operation(summary = "获取平台启动信息")
    @GetMapping
    public ApiResponse<PlatformBootstrapView> bootstrap() {
        List<ModuleSummaryView> modules = moduleDescriptors.orderedStream()
            .map(descriptor -> new ModuleSummaryView(
                descriptor.code(),
                descriptor.name(),
                descriptor.enabled(),
                descriptor.status()))
            .toList();
        return ApiResponse.success("平台启动信息已就绪", new PlatformBootstrapView(
            applicationName,
            "0.0.1-SNAPSHOT",
            modules,
            List.of(
                "通过 POST /api/auth/login 获取 JWT 访问令牌",
                "携带 Authorization: Bearer <accessToken> 调用业务接口",
                "令牌有效期由系统配置 auth.token.expire_hours 控制,默认 12 小时"
            )
        ));
    }
}
