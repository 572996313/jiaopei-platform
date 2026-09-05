package com.jiaopei.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "系统参数配置更新请求")
public record SystemConfigUpdateRequest(
    @NotBlank @Schema(description = "配置值") String configValue,
    @NotBlank @Size(max = 128) @Schema(description = "显示名称") String displayName,
    @Size(max = 500) @Schema(description = "配置说明") String description,
    @NotNull @Schema(description = "排序") Integer sortOrder,
    @NotBlank @Size(max = 32) @Schema(description = "状态") String status
) {
}
