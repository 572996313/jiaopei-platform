package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "权限码保护的后端接口信息")
public record PermissionEndpointInfoView(
    @Schema(description = "HTTP 方法,多个用逗号分隔,ANY 表示未限定") String method,
    @Schema(description = "请求路径") String path,
    @Schema(description = "接口说明(来自 @Operation summary,可能为空)") String summary
) {
}
