package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录页可用的第三方渠道")
public record AuthProviderView(
    @Schema(description = "渠道编码") String code,
    @Schema(description = "渠道名称") String name,
    @Schema(description = "图标标识") String icon,
    @Schema(description = "渠道类型") String type
) {
}
