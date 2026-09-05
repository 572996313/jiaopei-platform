package com.jiaopei.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "系统参数配置创建请求")
public record SystemConfigCreateRequest(
    @NotBlank @Size(max = 128) @Schema(description = "配置键") String configKey,
    @NotBlank @Schema(description = "配置值") String configValue,
    @NotBlank @Size(max = 32) @Schema(description = "值类型") String valueType,
    @NotBlank @Size(max = 64) @Schema(description = "配置分组") String configGroup,
    @NotBlank @Size(max = 128) @Schema(description = "显示名称") String displayName,
    @Size(max = 500) @Schema(description = "配置说明") String description,
    @Schema(description = "默认值") String defaultValue,
    @NotNull @Schema(description = "是否系统配置") Boolean isSystem,
    @NotNull @Schema(description = "排序") Integer sortOrder,
    @NotBlank @Size(max = 32) @Schema(description = "状态") String status
) {
}
