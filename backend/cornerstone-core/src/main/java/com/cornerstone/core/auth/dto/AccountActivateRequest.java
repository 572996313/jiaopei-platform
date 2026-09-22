package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "预建账号激活请求")
public record AccountActivateRequest(
    @Schema(description = "激活码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "激活码不能为空") @Size(max = 64, message = "激活码长度不能超过64个字符") String code,

    @Schema(description = "自设密码(需满足系统密码强度策略)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空") @Size(max = 128, message = "密码长度不能超过128个字符") String password
) {
}
