package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "携带权限码的菜单按钮节点引用")
public record PermissionMenuNodeRefView(
    @Schema(description = "菜单节点 ID") Long menuId,
    @Schema(description = "节点名称") String menuName,
    @Schema(description = "节点编码") String menuCode
) {
}
