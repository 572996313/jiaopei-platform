package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 数据字典里的一个字段。
 *
 * <p>数据库侧字段（列名/类型/可空/默认值/注释/主键）来自 INFORMATION_SCHEMA；
 * Java 侧字段（字段名/类型/@Schema）来自实体反射。两边对不上时（例如库里加了列但实体没跟上），
 * Java 侧字段为空，前端会标出来——这本身就是一条漂移检查。</p>
 *
 * @param columnName        列名
 * @param columnType        完整列类型，如 varchar(64)
 * @param nullable          是否允许为空
 * @param defaultValue      列默认值（原样字符串）
 * @param comment           列注释（中文，AGENTS.md 硬约定）
 * @param primaryKey        是否主键
 * @param extra             列附加属性，如 auto_increment
 * @param javaField         实体字段名；库里没有对应实体字段时为空
 * @param javaType          Java 类型简称
 * @param schemaDescription 实体 @Schema 中文说明
 * @param allowableValues   枚举型字段的允许码值
 */
@Schema(description = "数据字典字段：库内定义 + 实体注解")
public record SchemaColumnView(
    @Schema(description = "列名") String columnName,
    @Schema(description = "完整列类型") String columnType,
    @Schema(description = "是否允许为空") boolean nullable,
    @Schema(description = "列默认值") String defaultValue,
    @Schema(description = "列注释") String comment,
    @Schema(description = "是否主键") boolean primaryKey,
    @Schema(description = "列附加属性") String extra,
    @Schema(description = "实体字段名，null=库里没有对应实体字段") String javaField,
    @Schema(description = "Java 类型简称") String javaType,
    @Schema(description = "@Schema 中文说明") String schemaDescription,
    @Schema(description = "枚举允许码值") List<String> allowableValues
) {
}
