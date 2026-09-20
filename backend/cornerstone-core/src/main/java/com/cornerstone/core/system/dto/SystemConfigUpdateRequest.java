package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "系统参数配置更新请求")
public record SystemConfigUpdateRequest(
    @NotBlank(message = "参数值不能为空") @Schema(description = "配置值") String configValue,
    @NotBlank(message = "显示名称不能为空") @Size(max = 128, message = "显示名称长度不能超过128个字符") @Schema(description = "显示名称") String displayName,
    @Size(max = 500, message = "参数描述长度不能超过500个字符") @Schema(description = "配置说明") String description,
    @NotNull(message = "排序值不能为空") @Schema(description = "排序") Integer sortOrder,
    @NotBlank(message = "状态不能为空") @Size(max = 32, message = "状态长度不能超过32个字符") @Schema(description = "状态") String status
) {
}
