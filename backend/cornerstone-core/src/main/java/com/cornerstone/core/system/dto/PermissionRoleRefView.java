package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "持有权限码的角色引用")
public record PermissionRoleRefView(
    @Schema(description = "角色 ID") Long roleId,
    @Schema(description = "角色编码") String roleCode,
    @Schema(description = "角色名称") String roleName,
    @Schema(description = "该角色是否在「预设权限」里直接授予此码(sys_role.permission_codes CSV)") boolean fromPreset,
    @Schema(description = "该角色是否通过勾选携带此码的菜单节点获得授权") boolean fromMenuNode
) {
}
