package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.tenant.dto.CurrentInstitutionUpdateRequest;
import com.cornerstone.core.tenant.dto.CurrentInstitutionView;
import com.cornerstone.core.tenant.service.InstitutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/institution")
@Tag(name = "系统管理")
public class SystemInstitutionController {

    private final InstitutionService institutionService;

    public SystemInstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @Operation(summary = "获取当前机构信息")
    @GetMapping("/current")
    @RequiresPermission("tenant:read")
    public ApiResponse<CurrentInstitutionView> current() {
        Long institutionId = AuthContext.requireUser().orgId();
        return ApiResponse.success("当前机构信息已加载",
            institutionService.getCurrentInstitution(institutionId));
    }

    @Operation(summary = "更新当前机构信息")
    @PutMapping("/current")
    @RequiresPermission("tenant:write")
    public ApiResponse<CurrentInstitutionView> update(@Valid @RequestBody CurrentInstitutionUpdateRequest request) {
        Long institutionId = AuthContext.requireUser().orgId();
        return ApiResponse.success("当前机构信息已更新",
            institutionService.updateCurrentInstitution(institutionId, request));
    }
}
