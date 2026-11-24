package com.cornerstone.core.system.schema;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 一张表在管理界面上的维护入口。
 *
 * <p>数据字典只读库里的事实，但用户看完的下一步往往是"那我去哪改"。这里把
 * 表与承载它维护的页面绑起来，页面直接渲染成跳转按钮。</p>
 *
 * @param tableName 表名，必须与 INFORMATION_SCHEMA 中的 TABLE_NAME 完全一致
 * @param label     入口按钮上的中文名，如「字典管理」
 * @param path      管理端路由，如 /system/dicts；为空表示该表只有代码侧入口
 * @param remark    一句话说明这个入口能改什么，供字典页展示
 */
@Schema(description = "表的维护入口：表名 → 管理端页面")
public record SchemaMaintenanceEntry(
    @Schema(description = "表名") String tableName,
    @Schema(description = "入口中文名") String label,
    @Schema(description = "管理端路由，如 /system/dicts") String path,
    @Schema(description = "入口说明") String remark
) {
}
