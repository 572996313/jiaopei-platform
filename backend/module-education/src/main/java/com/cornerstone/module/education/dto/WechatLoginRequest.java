package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "微信小程序登录请求")
public record WechatLoginRequest(
    @Schema(description = "wx.login 获取的临时登录凭证code,一次性有效,5分钟过期",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "微信登录code不能为空")
    String code
) {
}
