package com.jiaopei.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "引用权限码的列表页按钮配置引用")
public record PermissionConfigButtonRefView(
    @Schema(description = "列表配置资源编码") String resourceCode,
    @Schema(description = "列表配置资源名称") String resourceName,
    @Schema(description = "按钮动作名") String action,
    @Schema(description = "按钮文案") String label,
    @Schema(description = "按钮位置:toolbar=表格上方,row=行内") String position
) {
}
