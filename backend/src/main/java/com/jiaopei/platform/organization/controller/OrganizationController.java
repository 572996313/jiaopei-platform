package com.jiaopei.platform.organization.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.organization.dto.OrganizationBootstrapView;
import com.jiaopei.platform.organization.dto.OrganizationSchemaView;
import com.jiaopei.platform.organization.service.OrganizationApplicationService;
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

    @GetMapping("/bootstrap")
    @RequiresPermission("organization:read")
    public ApiResponse<OrganizationBootstrapView> bootstrap() {
        return ApiResponse.success("组织模块启动信息已就绪",
            organizationApplicationService.getBootstrapView());
    }

    @GetMapping("/schema")
    @RequiresPermission("organization:read")
    public ApiResponse<OrganizationSchemaView> schema() {
        return ApiResponse.success("组织模块表结构元数据已就绪",
            organizationApplicationService.getSchemaView());
    }
}
