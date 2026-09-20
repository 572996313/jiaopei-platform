package com.cornerstone.module.organization.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.organization.dto.OrganizationBootstrapView;
import com.cornerstone.module.organization.dto.OrganizationSchemaView;
import com.cornerstone.module.organization.service.OrganizationApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
@Tag(name = "组织管理")
public class OrganizationController {

    private final OrganizationApplicationService organizationApplicationService;

    public OrganizationController(OrganizationApplicationService organizationApplicationService) {
        this.organizationApplicationService = organizationApplicationService;
    }

    @Operation(summary = "获取组织模块启动信息")
    @GetMapping("/bootstrap")
    @RequiresPermission("organization:read")
    public ApiResponse<OrganizationBootstrapView> bootstrap() {
        return ApiResponse.success("组织模块启动信息已就绪",
            organizationApplicationService.getBootstrapView());
    }

    @Operation(summary = "获取组织模块表结构元数据")
    @GetMapping("/schema")
    @RequiresPermission("organization:read")
    public ApiResponse<OrganizationSchemaView> schema() {
        return ApiResponse.success("组织模块表结构元数据已就绪",
            organizationApplicationService.getSchemaView());
    }
}
