package com.jiaopei.platform.system.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.auth.context.AuthContext;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.organization.dto.CurrentInstitutionUpdateRequest;
import com.jiaopei.platform.organization.dto.CurrentInstitutionView;
import com.jiaopei.platform.organization.service.InstitutionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/institution")
@Tag(name = "System")
public class SystemInstitutionController {

    private final InstitutionService institutionService;

    public SystemInstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @GetMapping("/current")
    @RequiresPermission("organization:read")
    public ApiResponse<CurrentInstitutionView> current() {
        Long institutionId = AuthContext.requireUser().orgId();
        return ApiResponse.success("Current institution loaded",
            institutionService.getCurrentInstitution(institutionId));
    }

    @PutMapping("/current")
    @RequiresPermission("organization:write")
    public ApiResponse<CurrentInstitutionView> update(@Valid @RequestBody CurrentInstitutionUpdateRequest request) {
        Long institutionId = AuthContext.requireUser().orgId();
        return ApiResponse.success("Current institution updated",
            institutionService.updateCurrentInstitution(institutionId, request));
    }
}
