package com.jiaopei.platform.auth.context;

import java.util.Set;

public record AuthenticatedUser(
    Long userId,
    Long orgId,
    String username,
    Set<String> roles,
    Set<String> permissions,
    Boolean forceChangePassword,
    String portal
) {
}
