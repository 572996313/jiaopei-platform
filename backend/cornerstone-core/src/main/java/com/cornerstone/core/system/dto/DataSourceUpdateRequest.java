package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "选项数据源更新请求")
public record DataSourceUpdateRequest(
    @NotBlank(message = "数据源名称不能为空") @Size(max = 64, message = "数据源名称长度不能超过64个字符")
    @Schema(description = "数据源名称,如 机构列表", requiredMode = Schema.RequiredMode.REQUIRED)
    String sourceName,

    @Size(max = 128, message = "接口路径长度不能超过128个字符")
    @Schema(description = "接口路径(source_type=api 时必填),如 /organizations/institutions") String endpoint,

    @Size(max = 16, message = "请求方法长度不能超过16个字符")
    @Schema(description = "请求方法,默认 GET", allowableValues = {"GET", "POST"}) String method,

    @Size(max = 64, message = "值字段长度不能超过64个字符")
    @Schema(description = "选项值字段,缺省为 id") String valueKey,

    @Size(max = 64, message = "标签字段长度不能超过64个字符")
    @Schema(description = "选项标签字段,如 orgName") String labelKey,

    @Schema(description = "请求参数定义 JSON 数组,如 [{\"name\":\"institutionId\",\"required\":false}]") String paramDefs,

    @NotBlank(message = "数据源类型不能为空")
    @Schema(description = "数据源类型(api=接口数据,dict=字典)", allowableValues = {"api", "dict"}, requiredMode = Schema.RequiredMode.REQUIRED)
    String sourceType,

    @Size(max = 64, message = "字典类型编码长度不能超过64个字符")
    @Schema(description = "source_type=dict 时的字典类型编码") String dictType,

    @Size(max = 500, message = "说明长度不能超过500个字符") @Schema(description = "说明") String description,

    @NotBlank(message = "状态不能为空") @Size(max = 32, message = "状态长度不能超过32个字符")
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,

    @Size(max = 500, message = "备注长度不能超过500个字符") @Schema(description = "备注") String remark
) {
}