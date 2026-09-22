package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "微信小程序自助注册请求,按所选身份或邀请码自动建档并分配对应角色")
public record WechatRegisterRequest(
    @Schema(description = "wx.login 获取的临时登录凭证code,一次性有效,5分钟过期",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "微信登录code不能为空")
    String code,

    @Schema(description = "注册身份(STUDENT=学生,PARENT=家长);未填邀请码时必填,填了邀请码则以邀请码固定角色为准",
            allowableValues = {"STUDENT", "PARENT"})
    @Size(max = 16, message = "注册身份长度不能超过16个字符")
    String role,

    @Schema(description = "邀请码,选填;填写后账号的机构与角色由邀请码固定,仅支持学生/家长角色")
    @Size(max = 64, message = "邀请码长度不能超过64个字符")
    String invitationCode,

    @Schema(description = "昵称,选填;留空时显示为\"微信用户\"")
    @Size(max = 32, message = "昵称不能超过32个字符")
    String nickname
) {
}
