package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 导航编排页的一行：只包含"编排"要用的字段，菜单的其他属性在本页只读。
 *
 * 与 MenuView 分开的理由是两页的授权口径不同——菜单管理能改路由与权限码，导航编排不能，
 * 所以这里不把 component/status/remark 透出去，避免前端多出一个改了不生效的输入框。
 */
@Schema(description = "导航编排节点")
public record NavigationView(
    @Schema(description = "菜单节点ID") Long id,
    @Schema(description = "父节点ID(空=顶层分组)") Long parentId,
    @Schema(description = "菜单编码(本页只读)") String menuCode,
    @Schema(description = "分组名或页面名(本页可改)") String menuName,
    @Schema(description = "节点类型(catalog=目录,menu=页面)") String menuType,
    @Schema(description = "前端路由路径(本页只读)") String path,
    @Schema(description = "权限码(本页只读)") String permissionCode,
    @Schema(description = "图标名称(本页可改)") String icon,
    @Schema(description = "排序号(顶层节点即分组自上而下的顺序)") Integer sortNo,
    @Schema(description = "是否在侧边栏可见(本页可改)") Boolean visible,
    @Schema(description = "是否属于底座段(true=「系统设置」整棵子树,本页只读)") Boolean locked,
    @Schema(
        description = "已授权该节点的角色数(目录为 0 时它下面的页面入口会整块不显示,是最常见的静默故障)",
        minimum = "0"
    )
    Long grantedRoleCount
) {
}
