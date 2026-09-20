package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据源消费者视图:前端按 sourceCode 读取该定义后,
 * 调 endpoint 拉数据并用 valueKey/labelKey 组装 {value,label} 下拉选项。
 */
@Schema(description = "选项数据源定义(消费者视图)")
public record DataSourceDefinitionView(
    @Schema(description = "数据源编码,如 institutions/roles") String sourceCode,
    @Schema(description = "数据源名称,如 机构列表") String sourceName,
    @Schema(description = "接口路径,如 /organizations/institutions") String endpoint,
    @Schema(description = "请求方法(GET/POST)") String method,
    @Schema(description = "选项值字段,缺省为 id") String valueKey,
    @Schema(description = "选项标签字段,如 orgName") String labelKey,
    @Schema(description = "请求参数定义 JSON 数组,如 [{\"name\":\"institutionId\",\"required\":false}]") String paramDefs,
    @Schema(description = "数据源类型(api=接口数据,dict=字典)", allowableValues = {"api", "dict"}) String sourceType,
    @Schema(description = "source_type=dict 时的字典类型编码") String dictType
) {
}