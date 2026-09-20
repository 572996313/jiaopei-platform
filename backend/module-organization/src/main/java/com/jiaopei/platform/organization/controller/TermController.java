package com.jiaopei.platform.organization.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.organization.domain.entity.TermEntity;
import com.jiaopei.platform.organization.dto.TermCreateRequest;
import com.jiaopei.platform.organization.dto.TermUpdateRequest;
import com.jiaopei.platform.organization.service.TermService;
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
@RequestMapping("/api/organizations/terms")
@Tag(name = "组织管理")
public class TermController {

    private final TermService termService;

    public TermController(TermService termService) {
        this.termService = termService;
    }

    @Operation(summary = "获取学期列表")
    @GetMapping
    @RequiresPermission("organization:read")
    public ApiResponse<List<TermEntity>> list(@RequestParam(required = false) String institutionId) {
        return ApiResponse.success("学期列表已加载", termService.listTerms(ExternalIdParser.parseOptional(institutionId, "institutionId")));
    }

    @Operation(summary = "创建学期")
    @PostMapping
    @RequiresPermission("organization:write")
    public ApiResponse<TermEntity> create(@Valid @RequestBody TermCreateRequest request) {
        return ApiResponse.success("学期已创建", termService.createTerm(request));
    }

    @Operation(summary = "更新学期")
    @PutMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<TermEntity> update(@PathVariable Long id,
                                          @Valid @RequestBody TermUpdateRequest request) {
        return ApiResponse.success("学期已更新", termService.updateTerm(id, request));
    }

    @Operation(summary = "删除学期")
    @DeleteMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        termService.deleteTerm(id);
        return ApiResponse.success("学期已删除", null);
    }
}
