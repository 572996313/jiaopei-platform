package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "功能权限总览视图:以 @RequiresPermission 注解扫描为权威源,聚合权限码与接口/角色/按钮/菜单节点的关系")
public record PermissionOverviewView(
    @Schema(description = "已注册权限码列表(后端注解扫描结果)") List<PermissionUsageView> permissions,
    @Schema(description = "未登记权限码:出现在菜单节点/按钮配置/角色中,但后端无任何接口引用(疑似假权限)") List<UnregisteredPermissionView> unregistered
) {
}
