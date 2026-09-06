package com.jiaopei.platform.auth.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.auth.dto.RoleCreateRequest;
import com.jiaopei.platform.auth.dto.RoleUpdateRequest;
import com.jiaopei.platform.auth.dto.RoleView;
import com.jiaopei.platform.auth.service.SystemRoleService;
import com.jiaopei.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/roles")
@Tag(name = "系统管理")
public class SystemRoleController {

    private final SystemRoleService systemRoleService;

    public SystemRoleController(SystemRoleService systemRoleService) {
        this.systemRoleService = systemRoleService;
    }

    @GetMapping
    @RequiresPermission("system:role:manage")
    public ApiResponse<List<RoleView>> list() {
        return ApiResponse.success("角色列表已加载", systemRoleService.listRoles());
    }

    @PostMapping
    @RequiresPermission("system:role:manage")
    public ApiResponse<RoleView> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.success("角色已创建", systemRoleService.createRole(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:role:manage")
    public ApiResponse<RoleView> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.success("角色已更新", systemRoleService.updateRole(id, request));
    }
}
