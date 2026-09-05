package com.jiaopei.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "系统参数配置视图")
public record SystemConfigView(
    @Schema(description = "主键") Long id,
    @Schema(description = "配置键") String configKey,
    @Schema(description = "配置值") String configValue,
    @Schema(description = "值类型：string/int/boolean/json") String valueType,
    @Schema(description = "配置分组") String configGroup,
    @Schema(description = "显示名称") String displayName,
    @Schema(description = "配置说明") String description,
    @Schema(description = "默认值") String defaultValue,
    @Schema(description = "是否系统配置") Boolean isSystem,
    @Schema(description = "排序") Integer sortOrder,
    @Schema(description = "状态") String status
) {
}
