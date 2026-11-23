package com.cornerstone.core.tenant.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.dto.InstitutionCreateRequest;
import com.cornerstone.core.tenant.service.InstitutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/institutions")
@Tag(name = "组织管理")
public class InstitutionController {

    private final InstitutionService institutionService;

    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @Operation(summary = "获取机构列表", description = "登录即可；平台管理员返回全量，其他账号只返回本机构")
    @GetMapping
    public ApiResponse<List<InstitutionEntity>> list() {
        return ApiResponse.success("机构列表已加载", institutionService.listInstitutions());
    }

    @Operation(summary = "创建机构")
    @PostMapping
    @RequiresPermission("tenant:write")
    public ApiResponse<InstitutionEntity> create(@Valid @RequestBody InstitutionCreateRequest request) {
        return ApiResponse.success("机构已创建", institutionService.createInstitution(request));
    }
}
