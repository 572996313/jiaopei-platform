package com.jiaopei.platform.auth.dto;

import java.util.List;

public record CurrentUserResponse(
    Long userId,
    Long orgId,
    Long institutionId,
    Long defaultCampusId,
    String username,
    String displayName,
    List<String> roles,
    List<String> permissions,
    Boolean passwordResetRequired,
    Boolean forceChangePassword
) {
}
