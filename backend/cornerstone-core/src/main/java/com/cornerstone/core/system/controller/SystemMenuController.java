package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.MenuCreateRequest;
import com.cornerstone.core.system.dto.MenuUpdateRequest;
import com.cornerstone.core.system.dto.MenuView;
import com.cornerstone.core.system.dto.RoleMenuUpdateRequest;
import com.cornerstone.core.system.service.SystemMenuService;
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

@RestController
@RequestMapping("/api/system")
@Tag(name = "系统管理")
public class SystemMenuController {

    private final SystemMenuService systemMenuService;

    public SystemMenuController(SystemMenuService systemMenuService) {
        this.systemMenuService = systemMenuService;
    }

    @Operation(summary = "获取菜单树")
    @GetMapping("/menus/tree")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<List<MenuView>> tree() {
        return ApiResponse.success("菜单树已加载", systemMenuService.tree());
    }

    @Operation(summary = "获取菜单列表")
    @GetMapping("/menus")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<List<MenuView>> list() {
        return ApiResponse.success("菜单列表已加载", systemMenuService.listMenus());
    }

    @Operation(summary = "创建菜单")
    @PostMapping("/menus")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<MenuView> create(@Valid @RequestBody MenuCreateRequest request) {
        return ApiResponse.success("菜单已创建", systemMenuService.createMenu(request));
    }

    @Operation(summary = "更新菜单")
    @PutMapping("/menus/{id}")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<MenuView> update(@PathVariable Long id, @Valid @RequestBody MenuUpdateRequest request) {
        return ApiResponse.success("菜单已更新", systemMenuService.updateMenu(id, request));
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/menus/{id}")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        systemMenuService.deleteMenu(id);
        return ApiResponse.success("菜单已删除", null);
    }

    @Operation(summary = "获取角色已勾选菜单 ID")
    @GetMapping("/roles/{roleId}/menus")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<List<Long>> roleMenus(@PathVariable Long roleId) {
        return ApiResponse.success("角色菜单已加载", systemMenuService.getRoleMenuIds(roleId));
    }

    @Operation(summary = "更新角色菜单分配")
    @PutMapping("/roles/{roleId}/menus")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<List<Long>> updateRoleMenus(@PathVariable Long roleId,
                                                   @Valid @RequestBody RoleMenuUpdateRequest request) {
        return ApiResponse.success("角色菜单已更新", systemMenuService.updateRoleMenus(roleId, request));
    }
}
