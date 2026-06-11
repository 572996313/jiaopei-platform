package com.jiaopei.platform.auth.dto;

import java.util.List;

public record LoginResponse(
    Long userId,
    Long orgId,
    String username,
    String displayName,
    List<String> roles,
    List<String> permissions,
    String tokenType,
    String accessToken,
    Boolean forceChangePassword
) {
}
