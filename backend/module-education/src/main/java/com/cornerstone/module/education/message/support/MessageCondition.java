package com.cornerstone.module.education.message.support;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 一条白名单条件：{@code column op value}。
 *
 * <p>不是 SQL 片段。column 必须是数据源视图里真实存在的列，op 只允许
 * eq/ne/gt/gte/lt/lte/contains/in，value 只做字面量比较。</p>
 */
@Schema(description = "事件过滤条件(视图列 + 比较符 + 字面量,不是 SQL)")
public record MessageCondition(
    @Schema(description = "视图列名(只允许字母、数字、下划线)") String column,
    @Schema(description = "比较符(eq/ne/gt/gte/lt/lte/contains/in)") String op,
    @Schema(description = "比较值；in 可传逗号分隔字符串或数组") Object value
) {
}
