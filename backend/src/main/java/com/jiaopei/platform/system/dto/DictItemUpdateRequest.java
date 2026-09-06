package com.jiaopei.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictItemUpdateRequest(
    @Schema(description = "条目名称(中文展示名)", example = "标准教室", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 128) String itemName,
    @Schema(description = "条目值(业务表单提交时存储的值)", example = "normal", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 128) String itemValue,
    @Schema(description = "排序号,越小越靠前")
    Integer sortNo,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 32) String status,
    @Schema(description = "备注")
    @Size(max = 500) String remark
) {
}
