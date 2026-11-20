package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.dto.RoleCreateRequest;
import com.cornerstone.core.auth.dto.RolePageQuery;
import com.cornerstone.core.auth.dto.RoleUpdateRequest;
import com.cornerstone.core.auth.dto.RoleView;
import com.cornerstone.core.auth.service.SystemRoleService;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.system.dto.RoleAuthorizationView;
import com.cornerstone.core.system.dto.RoleMenuUpdateRequest;
import com.cornerstone.core.system.service.SystemMenuService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/system/roles")
@Tag(name = "系统管理")
public class SystemRoleController {

    private final SystemRoleService systemRoleService;
    private final SystemMenuService systemMenuService;

    public SystemRoleController(SystemRoleService systemRoleService, SystemMenuService systemMenuService) {
        this.systemRoleService = systemRoleService;
        this.systemMenuService = systemMenuService;
    }

    @Operation(summary = "获取角色列表")
    @GetMapping
    @RequiresPermission("system:role:manage")
    public ApiResponse<List<RoleView>> list() {
        return ApiResponse.success("角色列表已加载", systemRoleService.listRoles());
    }

    @Operation(summary = "角色分页列表", description = "支持关键字(角色编码/角色名称)、状态筛选")
    @GetMapping("/page")
    @RequiresPermission("system:role:manage")
    public ApiResponse<PageResult<RoleView>> page(@Valid @ParameterObject RolePageQuery query) {
        return ApiResponse.success("角色分页列表已加载", systemRoleService.pageRoles(query));
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @RequiresPermission("system:role:manage")
    public ApiResponse<RoleView> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.success("角色已创建", systemRoleService.createRole(request));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    @RequiresPermission("system:role:manage")
    public ApiResponse<RoleView> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.success("角色已更新", systemRoleService.updateRole(id, request));
    }

    @Operation(summary = "获取角色授权", description = "返回角色已勾选的页面/操作节点、历史预设权限码与当前机构的数据范围")
    @GetMapping("/{roleId}/authorizations")
    @RequiresPermission("system:role:manage")
    public ApiResponse<RoleAuthorizationView> authorizations(
        @PathVariable Long roleId,
        @RequestParam(required = false) Long institutionId) {
        return ApiResponse.success("角色授权已加载",
            systemMenuService.getRoleAuthorization(roleId, institutionId));
    }

    @Operation(summary = "保存角色授权",
        description = "只接受按钮授权目录内的页面与操作节点；自动补目录祖先，保存后该角色在线会话立即失效")
    @PutMapping("/{roleId}/authorizations")
    @RequiresPermission("system:role:manage")
    public ApiResponse<RoleAuthorizationView> updateAuthorizations(
        @PathVariable Long roleId,
        @RequestParam(required = false) Long institutionId,
        @Valid @RequestBody RoleMenuUpdateRequest request) {
        return ApiResponse.success("角色授权已更新",
            systemMenuService.updateRoleAuthorization(roleId, request, institutionId));
    }
}
