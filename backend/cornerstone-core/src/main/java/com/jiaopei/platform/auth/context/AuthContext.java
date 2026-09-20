package com.jiaopei.platform.auth.context;

import java.util.Optional;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContext {

    private AuthContext() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return Optional.of(authenticatedUser);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser requireUser() {
        return currentUser().orElseThrow(
            () -> new InsufficientAuthenticationException("未获取到登录认证信息"));
    }

    public static boolean hasPermission(String permission) {
        return currentUser()
            .map(user -> user.permissions() != null && user.permissions().contains(permission))
            .orElse(false);
    }
}
