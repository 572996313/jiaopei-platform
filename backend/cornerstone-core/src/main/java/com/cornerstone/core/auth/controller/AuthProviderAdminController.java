package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.dto.AuthProviderAdminView;
import com.cornerstone.core.auth.dto.AuthProviderSaveRequest;
import com.cornerstone.core.auth.service.AuthProviderAdminService;
import com.cornerstone.core.auth.social.AuthProviderRegistry;
import com.cornerstone.core.common.api.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三方登录渠道配置的管理端接口。读写共用 {@code system:auth-provider:manage}，
 * 与「系统管理 → 第三方登录」页面节点同码。
 */
@RestController
@RequestMapping("/api/system/auth-providers")
@Tag(name = "第三方登录配置", description = "第三方登录渠道的端点、密钥与状态维护")
public class AuthProviderAdminController {

    private final AuthProviderAdminService authProviderAdminService;
    private final AuthProviderRegistry authProviderRegistry;

    public AuthProviderAdminController(AuthProviderAdminService authProviderAdminService,
                                       AuthProviderRegistry authProviderRegistry) {
        this.authProviderAdminService = authProviderAdminService;
        this.authProviderRegistry = authProviderRegistry;
    }

    @GetMapping
    @RequiresPermission("system:auth-provider:manage")
    @Operation(summary = "获取第三方登录渠道列表", description = "密钥只回传是否已配置，不回传密文")
    public ApiResponse<List<AuthProviderAdminView>> list() {
        return ApiResponse.success("第三方登录渠道已加载", authProviderAdminService.listProviders());
    }

    @GetMapping("/types")
    @RequiresPermission("system:auth-provider:manage")
    @Operation(summary = "获取可新增的渠道类型", description = "只返回当前已装配协议实现的类型")
    public ApiResponse<List<String>> types() {
        return ApiResponse.success("渠道类型已加载",
            authProviderRegistry.implementedTypes().stream().sorted().toList());
    }

    @PostMapping
    @RequiresPermission("system:auth-provider:manage")
    @Operation(summary = "新增第三方登录渠道", description = "标准 OAuth2 渠道填好端点与密钥即可接入，无需改代码")
    public ApiResponse<AuthProviderAdminView> create(@Valid @RequestBody AuthProviderSaveRequest request) {
        return ApiResponse.success("渠道已创建", authProviderAdminService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:auth-provider:manage")
    @Operation(summary = "更新第三方登录渠道", description = "渠道编码与类型不可修改；密钥留空表示保持原值")
    public ApiResponse<AuthProviderAdminView> update(@PathVariable Long id,
                                                     @Valid @RequestBody AuthProviderSaveRequest request) {
        return ApiResponse.success("渠道已更新", authProviderAdminService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:auth-provider:manage")
    @Operation(summary = "删除第三方登录渠道", description = "仍有账号绑定时拒绝删除，请改为停用")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        authProviderAdminService.delete(id);
        return ApiResponse.success("渠道已删除", null);
    }
}
