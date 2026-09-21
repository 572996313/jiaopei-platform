package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictTypeCreateRequest(
    @Schema(description = "字典类型编码,全局唯一,创建后不可修改", example = "room_type", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典类型编码不能为空") @Size(max = 64, message = "字典类型编码长度不能超过64个字符") String typeCode,
    @Schema(description = "字典类型名称", example = "示例类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典类型名称不能为空") @Size(max = 128, message = "字典类型名称长度不能超过128个字符") String typeName,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    @Size(max = 32, message = "状态长度不能超过32个字符") String status,
    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
