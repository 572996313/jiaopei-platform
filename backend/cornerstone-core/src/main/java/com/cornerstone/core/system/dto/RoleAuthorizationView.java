package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "角色当前的页面与按钮授权")
public record RoleAuthorizationView(
    @Schema(description = "角色ID") Long roleId,
    @Schema(description = "角色已授权的菜单节点ID(页面、按钮与目录)") List<Long> menuIds,
    @Schema(description = "角色历史预设权限码 CSV；普通授权界面不修改，仅用于展示锁定态")
    List<String> presetPermissionCodes
) {
}
