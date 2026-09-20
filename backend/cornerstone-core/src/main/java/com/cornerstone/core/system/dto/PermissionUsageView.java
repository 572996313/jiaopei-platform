package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "单个权限码的使用全景")
public record PermissionUsageView(
    @Schema(description = "权限码") String code,
    @Schema(description = "该权限码保护的后端接口清单") List<PermissionEndpointInfoView> endpoints,
    @Schema(description = "持有该权限码的角色(角色权限码 CSV ∪ 角色勾选的按钮节点)") List<PermissionRoleRefView> roles,
    @Schema(description = "携带该权限码的菜单按钮节点") List<PermissionMenuNodeRefView> menuNodes,
    @Schema(description = "引用该权限码的列表页按钮配置") List<PermissionConfigButtonRefView> configButtons,
    @Schema(description = "是否悬空码(后端无任何接口使用该码,前端配置不会产生实际门禁)") boolean dangling
) {
}
