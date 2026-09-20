package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DictItemView(
    @Schema(description = "主键ID")
    Long id,
    @Schema(description = "所属字典类型ID")
    Long typeId,
    @Schema(description = "条目编码,同一类型下唯一", example = "normal")
    String itemCode,
    @Schema(description = "条目名称(中文展示名)", example = "标准教室")
    String itemName,
    @Schema(description = "条目值(业务表单提交时存储的值)", example = "normal")
    String itemValue,
    @Schema(description = "排序号,越小越靠前")
    Integer sortNo,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
