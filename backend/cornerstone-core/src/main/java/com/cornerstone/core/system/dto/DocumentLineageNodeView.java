package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "业务单据节点")
public record DocumentLineageNodeView(
    @Schema(description = "单据编码") String code,
    @Schema(description = "单据中文名") String name,
    @Schema(description = "所属模块") String moduleKey,
    @Schema(description = "主表名") String primaryTable,
    @Schema(description = "附属表") List<String> relatedTables,
    @Schema(description = "管理端路由") String pagePath,
    @Schema(description = "页面权限码") String permissionCode,
    @Schema(description = "单据说明") String description,
    @Schema(description = "业务口径是否已确认") boolean confirmed,
    @Schema(description = "补充说明") String remark,
    @Schema(description = "是否为模块显式登记的单据；false 表示按数据字典补的全表节点") boolean declared
) {
}
