package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色授权弹窗里的一个按钮/操作项。
 *
 * <p>{@code grantedByNode} 表示角色已勾选承载该动作的按钮节点；
 * {@code grantedByCsv} 表示权限来自角色历史 {@code permission_codes} CSV，
 * 普通授权界面不允许撤销，需去高级视图清理。两者取并集才是实际生效状态。</p>
 */
@Schema(description = "按钮授权项：页面下的一个可授权操作")
public record ActionCatalogActionView(
    @Schema(description = "稳定业务键，与列表配置 actions_json 的 action 对齐")
    String actionKey,
    @Schema(description = "客户看到的中文按钮名")
    String label,
    @Schema(description = "一句话说明该按钮会让用户做什么")
    String description,
    @Schema(description = "依赖的权限码；纯前端动作(UI_ONLY)为空")
    String permissionCode,
    @Schema(description = "承载授权的按钮节点ID；纯前端动作为空")
    Long grantMenuId,
    @Schema(description = "承载授权的按钮节点编码；纯前端动作为空")
    String grantMenuCode,
    @Schema(description = "授权模式(standalone=可独立勾选,linked=同码联动,uiOnly=跟随页面)")
    String mode,
    @Schema(description = "是否高危动作(导出/重置密码/注销/批量导入/代点名等)")
    Boolean sensitive,
    @Schema(description = "同页展示顺序")
    Integer sortNo,
    @Schema(description = "是否已通过勾选按钮节点授予；未按角色查询时为空")
    Boolean grantedByNode,
    @Schema(description = "是否由角色历史预设权限码 CSV 授予；未按角色查询时为空")
    Boolean grantedByCsv,
    @Schema(description = "配置缺口说明(节点缺失、类型不符或码不一致时非空)")
    String gap
) {
}
