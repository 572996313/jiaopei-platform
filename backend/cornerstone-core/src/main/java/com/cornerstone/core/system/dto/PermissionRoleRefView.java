package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "持有权限码的角色引用")
public record PermissionRoleRefView(
    @Schema(description = "角色 ID") Long roleId,
    @Schema(description = "角色编码") String roleCode,
    @Schema(description = "角色名称") String roleName
) {
}
