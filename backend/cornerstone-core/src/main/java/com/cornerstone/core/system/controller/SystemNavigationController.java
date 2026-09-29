package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.NavigationCatalogCreateRequest;
import com.cornerstone.core.system.dto.NavigationCatalogCreateResultView;
import com.cornerstone.core.system.dto.NavigationUpdateRequest;
import com.cornerstone.core.system.dto.NavigationView;
import com.cornerstone.core.system.service.SystemNavigationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "系统管理")
public class SystemNavigationController {

    private final SystemNavigationService systemNavigationService;

    public SystemNavigationController(SystemNavigationService systemNavigationService) {
        this.systemNavigationService = systemNavigationService;
    }

    @Operation(summary = "获取导航编排结构", description = "返回管理端可编排的目录与页面节点（不含按钮），每个节点带已授权角色数")
    @GetMapping("/navigation")
    @RequiresPermission("system:nav:manage")
    public ApiResponse<List<NavigationView>> navigation() {
        return ApiResponse.success("导航结构已加载", systemNavigationService.listNavigation());
    }

    @Operation(summary = "保存导航编排", description = "批量更新组名、图标、顺序、所属目录与是否显示；整批校验通过才落库")
    @PutMapping("/navigation")
    @RequiresPermission("system:nav:manage")
    public ApiResponse<List<NavigationView>> saveNavigation(@Valid @RequestBody NavigationUpdateRequest request) {
        return ApiResponse.success("导航已保存", systemNavigationService.saveNavigation(request));
    }

    @Operation(summary = "新建导航分组", description = "新建顶层目录并同批授权给所有在职角色，返回新节点 ID、授权角色数与最新导航结构")
    @PostMapping("/navigation/catalogs")
    @RequiresPermission("system:nav:manage")
    public ApiResponse<NavigationCatalogCreateResultView> createCatalog(
        @Valid @RequestBody NavigationCatalogCreateRequest request) {
        return ApiResponse.success("分组已创建并授权", systemNavigationService.createCatalog(request));
    }
}
