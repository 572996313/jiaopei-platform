package com.jiaopei.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "微信绑定系统账号请求,校验账号密码后将当前微信openid与该用户绑定")
public record WechatBindRequest(
    @Schema(description = "wx.login 获取的临时登录凭证code,一次性有效,5分钟过期",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "微信登录code不能为空")
    String code,

    @Schema(description = "系统登录用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    String username,

    @Schema(description = "系统登录密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    String password
) {
}
