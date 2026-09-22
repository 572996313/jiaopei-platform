package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "邀请码注册请求")
public record InvitationRegisterRequest(
    @Schema(description = "邀请码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "邀请码不能为空") @Size(max = 64, message = "邀请码长度不能超过64个字符") String code,

    @Schema(description = "登录用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空") @Size(max = 64, message = "用户名长度不能超过64个字符") String username,

    @Schema(description = "显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "显示名称不能为空") @Size(max = 128, message = "显示名称长度不能超过128个字符") String displayName,

    @Schema(description = "手机号")
    @Size(max = 32, message = "手机号长度不能超过32个字符") String phone,

    @Schema(description = "密码(需满足系统密码强度策略)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空") @Size(max = 128, message = "密码长度不能超过128个字符") String password
) {
}
