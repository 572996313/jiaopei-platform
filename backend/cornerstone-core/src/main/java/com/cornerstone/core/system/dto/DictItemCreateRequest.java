package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DictItemCreateRequest(
    @Schema(description = "所属字典类型ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "字典类型不能为空") Long typeId,
    @Schema(description = "条目编码,同一类型下唯一", example = "normal", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典编码不能为空") @Size(max = 64, message = "字典编码长度不能超过64个字符") String itemCode,
    @Schema(description = "条目名称(中文展示名)", example = "示例条目", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典名称不能为空") @Size(max = 128, message = "字典名称长度不能超过128个字符") String itemName,
    @Schema(description = "条目值(业务表单提交时存储的值)", example = "normal", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典值不能为空") @Size(max = 128, message = "字典值长度不能超过128个字符") String itemValue,
    @Schema(description = "排序号,越小越靠前")
    Integer sortNo,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    @Size(max = 32, message = "状态长度不能超过32个字符") String status,
    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
