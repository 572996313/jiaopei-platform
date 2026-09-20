package com.cornerstone.module.organization.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.dto.CampusCreateRequest;
import com.cornerstone.module.organization.dto.CampusUpdateRequest;
import com.cornerstone.module.organization.service.CampusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/campuses")
@Tag(name = "组织管理")
public class CampusController {

    private final CampusService campusService;

    public CampusController(CampusService campusService) {
        this.campusService = campusService;
    }

    @Operation(summary = "获取校区列表")
    @GetMapping
    @RequiresPermission("organization:read")
    public ApiResponse<List<CampusEntity>> list(@RequestParam(required = false) Long institutionId) {
        return ApiResponse.success("校区列表已加载", campusService.listCampuses(institutionId));
    }

    @Operation(summary = "创建校区")
    @PostMapping
    @RequiresPermission("organization:write")
    public ApiResponse<CampusEntity> create(@Valid @RequestBody CampusCreateRequest request) {
        return ApiResponse.success("校区已创建", campusService.createCampus(request));
    }

    @Operation(summary = "更新校区")
    @PutMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<CampusEntity> update(@PathVariable Long id,
                                            @Valid @RequestBody CampusUpdateRequest request) {
        return ApiResponse.success("校区已更新", campusService.updateCampus(id, request));
    }

    @Operation(summary = "删除校区")
    @DeleteMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        campusService.deleteCampus(id);
        return ApiResponse.success("校区已删除", null);
    }
}
