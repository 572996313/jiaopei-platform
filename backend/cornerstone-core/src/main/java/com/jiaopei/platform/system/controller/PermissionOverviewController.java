package com.jiaopei.platform.system.controller;

import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.system.dto.PermissionOptionView;
import com.jiaopei.platform.system.dto.PermissionOverviewView;
import com.jiaopei.platform.system.service.PermissionOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "功能权限总览", description = "权限码与接口/角色/按钮/菜单节点关系的只读聚合视图")
public class PermissionOverviewController {

    private final PermissionOverviewService permissionOverviewService;

    public PermissionOverviewController(PermissionOverviewService permissionOverviewService) {
        this.permissionOverviewService = permissionOverviewService;
    }

    @GetMapping("/permission-overview")
    @Operation(summary = "功能权限总览", description = "聚合权限码与受控接口、持有角色、列表按钮配置、菜单按钮节点的关系,仅需登录,不要求额外权限")
    public ApiResponse<PermissionOverviewView> overview() {
        return ApiResponse.success("权限总览已加载", permissionOverviewService.overview());
    }

    @GetMapping("/permission-options")
    @Operation(summary = "权限码下拉选项", description = "全量可分配权限码(以 @RequiresPermission 注解扫描为权威源,合并启用按钮节点中文名),供角色弹窗预设权限、菜单按钮权限码下拉、列表权限标签回显使用;仅需登录,不要求额外权限")
    public ApiResponse<List<PermissionOptionView>> options() {
        return ApiResponse.success("权限选项已加载", permissionOverviewService.permissionOptions());
    }
}
