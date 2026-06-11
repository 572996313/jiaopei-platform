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
@Tag(name = "System")
public class PlatformBootstrapController {

    private final JiaopeiProperties properties;

    @Value("${spring.application.name}")
    private String applicationName;

    public PlatformBootstrapController(JiaopeiProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public ApiResponse<PlatformBootstrapView> bootstrap() {
        return ApiResponse.success("Platform bootstrap is ready", new PlatformBootstrapView(
            applicationName,
            "0.0.1-SNAPSHOT",
            List.of(
                new ModuleSummaryView("auth", "权限认证", true, "STUB_READY"),
                new ModuleSummaryView("organization", "组织主数据", properties.getModules().isOrganizationEnabled(), "READY"),
                new ModuleSummaryView("education", "教务", properties.getModules().isEducationEnabled(), "RESERVED")
            ),
            List.of(
                "Use POST /api/auth/login to obtain the current stub token",
                "Call organization endpoints with Authorization: Bearer stub-access-token",
                "Replace stub authentication with JWT/session implementation"
            )
        ));
    }
}
