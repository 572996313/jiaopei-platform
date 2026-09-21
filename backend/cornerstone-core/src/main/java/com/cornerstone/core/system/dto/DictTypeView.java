package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DictTypeView(
    @Schema(description = "主键ID")
    Long id,
    @Schema(description = "字典类型编码", example = "room_type")
    String typeCode,
    @Schema(description = "字典类型名称", example = "示例类型")
    String typeName,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
