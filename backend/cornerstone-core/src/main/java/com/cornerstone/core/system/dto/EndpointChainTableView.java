package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 服务声明依赖的 Mapper 对应的数据表。
 *
 * <p>表名取 Mapper 泛型实体上的 {@code @TableName}，表注释取数据字典。
 * 只表示"这个服务持有该表对应的 Mapper"，不区分读还是写。</p>
 */
@Schema(description = "链路涉及的数据表")
public record EndpointChainTableView(
    @Schema(description = "表名") String tableName,
    @Schema(description = "表中文注释；字典不可用时为空") String tableComment,
    @Schema(description = "所属模块") String moduleKey,
    @Schema(description = "对应的 Mapper 类名") String mapperClass
) {
}
