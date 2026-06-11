package com.jiaopei.platform.auth.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.auth.dto.ResetPasswordResponse;
import com.jiaopei.platform.auth.dto.UserCreateRequest;
import com.jiaopei.platform.auth.dto.UserRoleUpdateRequest;
import com.jiaopei.platform.auth.dto.UserUpdateRequest;
import com.jiaopei.platform.auth.dto.UserView;
import com.jiaopei.platform.auth.service.SystemUserService;
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
@RequestMapping("/api/system/users")
@Tag(name = "System")
public class SystemUserController {

    private final SystemUserService systemUserService;

    public SystemUserController(SystemUserService systemUserService) {
        this.systemUserService = systemUserService;
    }

    @GetMapping
    @RequiresPermission("system:user:manage")
    public ApiResponse<List<UserView>> list() {
        return ApiResponse.success("User list loaded", systemUserService.listUsers());
    }

    @PostMapping
    @RequiresPermission("system:user:manage")
    public ApiResponse<UserView> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success("User created", systemUserService.createUser(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:user:manage")
    public ApiResponse<UserView> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success("User updated", systemUserService.updateUser(id, request));
    }

    @PostMapping("/{id}/reset-password")
    @RequiresPermission("system:user:manage")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        return ApiResponse.success("Password reset", systemUserService.resetPassword(id));
    }

    @PostMapping("/{id}/roles")
    @RequiresPermission("system:user:manage")
    public ApiResponse<UserView> updateRoles(@PathVariable Long id, @Valid @RequestBody UserRoleUpdateRequest request) {
        return ApiResponse.success("User roles updated", systemUserService.updateRoles(id, request));
    }
}
