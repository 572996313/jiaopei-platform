package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "第三方登录换取令牌请求")
public record ExchangeTicketRequest(
    @Schema(description = "一次性登录票据", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录票据不能为空")
    String ticket
) {
}
