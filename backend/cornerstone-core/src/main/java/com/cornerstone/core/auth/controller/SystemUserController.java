package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.dto.ResetPasswordResponse;
import com.cornerstone.core.auth.dto.UserCreateRequest;
import com.cornerstone.core.auth.dto.UserPageQuery;
import com.cornerstone.core.auth.dto.UserRoleUpdateRequest;
import com.cornerstone.core.auth.dto.UserUpdateRequest;
import com.cornerstone.core.auth.dto.UserView;
import com.cornerstone.core.auth.service.SystemUserService;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/system/users")
@Tag(name = "系统管理")
public class SystemUserController {

    private final SystemUserService systemUserService;

    public SystemUserController(SystemUserService systemUserService) {
        this.systemUserService = systemUserService;
    }

    @Operation(summary = "获取用户列表")
    @GetMapping
    @RequiresPermission("system:user:manage")
    public ApiResponse<List<UserView>> list() {
        return ApiResponse.success("用户列表已加载", systemUserService.listUsers());
    }

    @Operation(summary = "用户分页列表", description = "支持关键字(账号/显示名称/手机号)、状态、机构筛选")
    @GetMapping("/page")
    @RequiresPermission("system:user:manage")
    public ApiResponse<PageResult<UserView>> page(@Valid @ParameterObject UserPageQuery query) {
        return ApiResponse.success("用户分页列表已加载", systemUserService.pageUsers(query));
    }

    @Operation(summary = "创建用户")
    @PostMapping
    @RequiresPermission("system:user:manage")
    public ApiResponse<UserView> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success("用户已创建", systemUserService.createUser(request));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    @RequiresPermission("system:user:manage")
    public ApiResponse<UserView> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success("用户已更新", systemUserService.updateUser(id, request));
    }

    @Operation(summary = "重置用户密码")
    @PostMapping("/{id}/reset-password")
    @RequiresPermission("system:user:manage")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        return ApiResponse.success("密码已重置", systemUserService.resetPassword(id));
    }

    @Operation(summary = "更新用户角色")
    @PostMapping("/{id}/roles")
    @RequiresPermission("system:user:manage")
    public ApiResponse<UserView> updateRoles(@PathVariable Long id, @Valid @RequestBody UserRoleUpdateRequest request) {
        return ApiResponse.success("用户角色已更新", systemUserService.updateRoles(id, request));
    }
}
