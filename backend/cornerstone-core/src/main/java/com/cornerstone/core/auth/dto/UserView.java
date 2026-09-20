package com.cornerstone.core.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserView(
    Long id,
    Long institutionId,
    String username,
    String displayName,
    String phone,
    String status,
    Boolean passwordResetRequired,
    LocalDateTime lastLoginAt,
    String remark,
    List<Long> roleIds,
    List<String> roleCodes
) {
}
