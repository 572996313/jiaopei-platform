package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "批量生成激活码的单行结果")
public record StudentAccountActivationView(
    @Schema(description = "账号ID") Long userId,

    @Schema(description = "登录账号") String username,

    @Schema(description = "账号显示名称") String displayName,

    @Schema(description = "手机号") String phone,

    @Schema(description = "激活码,未发码时为空") String code,

    @Schema(description = "激活码过期时间") LocalDateTime expiresAt,

    @Schema(description = "说明(跳过发码的原因或已复用原码提示)") String message
) {
}
