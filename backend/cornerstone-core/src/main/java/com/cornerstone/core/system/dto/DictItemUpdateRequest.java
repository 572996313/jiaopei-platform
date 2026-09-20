package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictItemUpdateRequest(
    @Schema(description = "条目名称(中文展示名)", example = "标准教室", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典名称不能为空") @Size(max = 128, message = "字典名称长度不能超过128个字符") String itemName,
    @Schema(description = "条目值(业务表单提交时存储的值)", example = "normal", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典值不能为空") @Size(max = 128, message = "字典值长度不能超过128个字符") String itemValue,
    @Schema(description = "排序号,越小越靠前")
    Integer sortNo,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "状态不能为空") @Size(max = 32, message = "状态长度不能超过32个字符") String status,
    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
