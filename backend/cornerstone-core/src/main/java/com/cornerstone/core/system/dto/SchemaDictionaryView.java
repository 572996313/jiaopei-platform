package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 数据字典整包：一次请求取全库表结构，前端在本地搜索与过滤。
 *
 * <p>表数量在几十张、字段几百个的量级，一次拉全比按表分页省事得多，
 * 也让"搜一个中文词跨表定位"变成纯前端过滤。</p>
 *
 * @param database            当前库名
 * @param generatedAt         生成时间（服务端缓存刷新时间）
 * @param tableCount          表数量
 * @param columnCount         字段总数
 * @param missingCommentCount 缺中文注释的字段数（按 AGENTS.md 应为 0，非 0 即违规）
 * @param tables              表清单
 */
@Schema(description = "数据字典整包：全库表结构与字段含义")
public record SchemaDictionaryView(
    @Schema(description = "当前库名") String database,
    @Schema(description = "生成时间") String generatedAt,
    @Schema(description = "表数量") int tableCount,
    @Schema(description = "字段总数") int columnCount,
    @Schema(description = "缺中文注释的字段数") int missingCommentCount,
    @Schema(description = "表清单") List<SchemaTableView> tables
) {
}
