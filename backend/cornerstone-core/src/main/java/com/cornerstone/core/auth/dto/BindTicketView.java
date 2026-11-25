package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "第三方账号绑定票据")
public record BindTicketView(
    @Schema(description = "绑定票据(跳转授权地址时携带)") String ticket
) {
}
