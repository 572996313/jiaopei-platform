package com.cornerstone.core.auth.dto;

public record ResetPasswordResponse(
    Long userId,
    String initialPassword,
    Boolean passwordResetRequired
) {
}
