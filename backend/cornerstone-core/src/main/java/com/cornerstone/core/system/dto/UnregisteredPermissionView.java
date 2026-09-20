package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "未登记权限码:菜单节点/按钮配置/角色中出现,但后端没有任何接口使用")
public record UnregisteredPermissionView(
    @Schema(description = "权限码") String code,
    @Schema(description = "持有该码的角色") List<PermissionRoleRefView> roles,
    @Schema(description = "携带该码的菜单节点") List<PermissionMenuNodeRefView> menuNodes,
    @Schema(description = "引用该码的列表按钮配置") List<PermissionConfigButtonRefView> configButtons
) {
}
