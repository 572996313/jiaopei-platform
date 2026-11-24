package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 界面上触发该接口的按钮。来源是各模块的 {@code ActionCatalogContributor} 登记，
 * 不额外手工维护；没有登记按钮的接口（如定时任务、内部调用）这里为空。
 */
@Schema(description = "触发接口的界面按钮")
public record EndpointChainActionView(
    @Schema(description = "所属页面的 menu_code") String pageMenuCode,
    @Schema(description = "按钮业务键") String actionKey,
    @Schema(description = "按钮中文名") String label,
    @Schema(description = "按钮说明") String description,
    @Schema(description = "授权模式：STANDALONE / LINKED / UI_ONLY") String mode,
    @Schema(description = "是否高危动作") boolean sensitive
) {
}
