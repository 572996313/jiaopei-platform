package com.jiaopei.platform.system.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.system.dto.MenuCreateRequest;
import com.jiaopei.platform.system.dto.MenuUpdateRequest;
import com.jiaopei.platform.system.dto.MenuView;
import com.jiaopei.platform.system.dto.RoleMenuUpdateRequest;
import com.jiaopei.platform.system.service.SystemMenuService;
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
@Tag(name = "System")
public class SystemMenuController {

    private final SystemMenuService systemMenuService;

    public SystemMenuController(SystemMenuService systemMenuService) {
        this.systemMenuService = systemMenuService;
    }

    @GetMapping("/menus/tree")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<List<MenuView>> tree() {
        return ApiResponse.success("Menu tree loaded", systemMenuService.tree());
    }

    @GetMapping("/menus")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<List<MenuView>> list() {
        return ApiResponse.success("Menu list loaded", systemMenuService.listMenus());
    }

    @PostMapping("/menus")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<MenuView> create(@Valid @RequestBody MenuCreateRequest request) {
        return ApiResponse.success("Menu created", systemMenuService.createMenu(request));
    }

    @PutMapping("/menus/{id}")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<MenuView> update(@PathVariable Long id, @Valid @RequestBody MenuUpdateRequest request) {
        return ApiResponse.success("Menu updated", systemMenuService.updateMenu(id, request));
    }

    @DeleteMapping("/menus/{id}")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        systemMenuService.deleteMenu(id);
        return ApiResponse.success("Menu deleted", null);
    }

    @GetMapping("/roles/{roleId}/menus")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<List<Long>> roleMenus(@PathVariable Long roleId) {
        return ApiResponse.success("Role menus loaded", systemMenuService.getRoleMenuIds(roleId));
    }

    @PutMapping("/roles/{roleId}/menus")
    @RequiresPermission("system:menu:manage")
    public ApiResponse<List<Long>> updateRoleMenus(@PathVariable Long roleId,
                                                   @Valid @RequestBody RoleMenuUpdateRequest request) {
        return ApiResponse.success("Role menus updated", systemMenuService.updateRoleMenus(roleId, request));
    }
}
