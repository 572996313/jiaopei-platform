package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "第三方登录选择账号请求")
public record SelectAccountRequest(
    @Schema(description = "一次性登录票据", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录票据不能为空")
    String ticket,

    @Schema(description = "账号键(来自选择列表，不暴露系统用户ID)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择要登录的账号")
    String accountKey
) {
}
