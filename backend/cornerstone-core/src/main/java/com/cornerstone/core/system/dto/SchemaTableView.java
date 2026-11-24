package com.cornerstone.core.system.dto;

import com.cornerstone.core.system.schema.SchemaMaintenanceEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 数据字典里的一张表。
 *
 * @param tableName    表名
 * @param tableComment 表注释（中文）
 * @param moduleKey    所属模块：core / organization / education / other，由实体所在包推导
 * @param entityClass  对应实体类全名；没有实体（视图、遗留表）时为空
 * @param columns      字段清单，按列在库中的顺序
 * @param maintenance  维护入口，未登记时为 null
 */
@Schema(description = "数据字典表：表名、注释、所属模块与字段清单")
public record SchemaTableView(
    @Schema(description = "表名") String tableName,
    @Schema(description = "表注释") String tableComment,
    @Schema(description = "所属模块") String moduleKey,
    @Schema(description = "对应实体类全名") String entityClass,
    @Schema(description = "字段清单") List<SchemaColumnView> columns,
    @Schema(description = "维护入口") SchemaMaintenanceEntry maintenance
) {
}
