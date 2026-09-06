package com.jiaopei.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictTypeUpdateRequest(
    @Schema(description = "字典类型名称", example = "教室类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 128) String typeName,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 32) String status,
    @Schema(description = "备注")
    @Size(max = 500) String remark
) {
}
