package com.jiaopei.platform.organization.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.dto.CampusCreateRequest;
import com.jiaopei.platform.organization.service.CampusService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/campuses")
@Tag(name = "Organization")
public class CampusController {

    private final CampusService campusService;

    public CampusController(CampusService campusService) {
        this.campusService = campusService;
    }

    @GetMapping
    @RequiresPermission("organization:read")
    public ApiResponse<List<CampusEntity>> list(@RequestParam(required = false) Long institutionId) {
        return ApiResponse.success("Campus list loaded", campusService.listCampuses(institutionId));
    }

    @PostMapping
    @RequiresPermission("organization:write")
    public ApiResponse<CampusEntity> create(@Valid @RequestBody CampusCreateRequest request) {
        return ApiResponse.success("Campus placeholder created", campusService.createCampus(request));
    }
}
