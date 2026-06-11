package com.jiaopei.platform.auth.dto;

public record ResetPasswordResponse(
    Long userId,
    String initialPassword,
    Boolean passwordResetRequired
) {
}
