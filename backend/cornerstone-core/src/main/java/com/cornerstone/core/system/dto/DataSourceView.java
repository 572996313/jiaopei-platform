package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "选项数据源配置视图")
public record DataSourceView(
    @Schema(description = "主键") Long id,
    @Schema(description = "数据源编码,唯一标识,如 institutions/roles") String sourceCode,
    @Schema(description = "数据源名称,如 机构列表") String sourceName,
    @Schema(description = "接口路径,如 /organizations/institutions") String endpoint,
    @Schema(description = "请求方法(GET/POST)") String method,
    @Schema(description = "选项值字段,缺省为 id") String valueKey,
    @Schema(description = "选项标签字段,如 orgName") String labelKey,
    @Schema(description = "请求参数定义 JSON 数组,如 [{\"name\":\"institutionId\",\"required\":false}]") String paramDefs,
    @Schema(description = "数据源类型(api=接口数据,dict=字典)", allowableValues = {"api", "dict"}) String sourceType,
    @Schema(description = "source_type=dict 时的字典类型编码") String dictType,
    @Schema(description = "说明") String description,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"}) String status,
    @Schema(description = "备注") String remark
) {
}