package com.jiaopei.platform.system.controller;

import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.config.properties.JiaopeiProperties;
import com.jiaopei.platform.system.dto.ModuleSummaryView;
import com.jiaopei.platform.system.dto.PlatformBootstrapView;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/bootstrap")
@Tag(name = "系统管理")
public class PlatformBootstrapController {

    private final JiaopeiProperties properties;

    @Value("${spring.application.name}")
    private String applicationName;

    public PlatformBootstrapController(JiaopeiProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public ApiResponse<PlatformBootstrapView> bootstrap() {
        return ApiResponse.success("平台启动信息已就绪", new PlatformBootstrapView(
            applicationName,
            "0.0.1-SNAPSHOT",
            List.of(
                new ModuleSummaryView("auth", "权限认证", true, "STUB 就绪"),
                new ModuleSummaryView("organization", "组织主数据", properties.getModules().isOrganizationEnabled(), "就绪"),
                new ModuleSummaryView("education", "教务", properties.getModules().isEducationEnabled(), "预留")
            ),
            List.of(
                "通过 POST /api/auth/login 获取当前 stub 令牌",
                "携带 Authorization: Bearer stub-access-token 调用组织模块接口",
                "后续将用 JWT/会话实现替换 stub 鉴权"
            )
        ));
    }
}
