package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "单个权限码的使用全景")
public record PermissionUsageView(
    @Schema(description = "权限码") String code,
    @Schema(description = "该权限码保护的后端接口清单") List<PermissionEndpointInfoView> endpoints,
    @Schema(description = "持有该权限码的角色(预设权限 CSV ∪ 勾选了携带该码的菜单节点),来源分别用 fromPreset/fromMenuNode 标出")
    List<PermissionRoleRefView> roles,
    @Schema(description = "携带该权限码的菜单节点(页面节点给读取码,按钮节点给写操作码)") List<PermissionMenuNodeRefView> menuNodes,
    @Schema(description = "引用该权限码的列表页按钮配置") List<PermissionConfigButtonRefView> configButtons
) {
}
