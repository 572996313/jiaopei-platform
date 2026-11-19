package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "按页面分组的按钮授权目录")
public record ActionCatalogPageView(
    @Schema(description = "页面菜单节点ID") Long menuId,
    @Schema(description = "页面菜单编码") String menuCode,
    @Schema(description = "页面名称(客户可在菜单管理/导航编排改名)") String menuName,
    @Schema(description = "前端路由路径") String path,
    @Schema(description = "进入本页所需的读取码；仅需登录的页面为空") String readPermissionCode,
    @Schema(description = "本页可授权的操作") List<ActionCatalogActionView> actions
) {
}
