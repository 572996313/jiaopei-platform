package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "微信小程序自助注册请求,按所选身份自动建档并分配对应角色")
public record WechatRegisterRequest(
    @Schema(description = "wx.login 获取的临时登录凭证code,一次性有效,5分钟过期",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "微信登录code不能为空")
    String code,

    @Schema(description = "注册身份(STUDENT=学生,PARENT=家长)", allowableValues = {"STUDENT", "PARENT"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "注册身份不能为空")
    @Pattern(regexp = "STUDENT|PARENT", message = "注册身份仅支持 STUDENT 或 PARENT")
    String role,

    @Schema(description = "昵称,选填;留空时显示为\"微信用户\"")
    @Size(max = 32, message = "昵称不能超过32个字符")
    String nickname
) {
}
