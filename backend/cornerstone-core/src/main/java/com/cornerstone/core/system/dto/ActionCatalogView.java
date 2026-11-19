package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "角色授权目录：页面与操作清单，附配置缺口")
public record ActionCatalogView(
    @Schema(description = "按页面分组的可授权操作") List<ActionCatalogPageView> pages,
    @Schema(description = "代码登记与数据库节点对不上的缺口，正常应为空") List<ActionCatalogGapView> gaps
) {
}
