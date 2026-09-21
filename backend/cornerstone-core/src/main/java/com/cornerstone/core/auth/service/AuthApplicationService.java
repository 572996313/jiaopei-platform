package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.dto.ChangePasswordRequest;
import com.cornerstone.core.auth.dto.CurrentUserResponse;
import com.cornerstone.core.auth.dto.LoginRequest;
import com.cornerstone.core.auth.dto.LoginResponse;

public interface AuthApplicationService {

    String FORCE_CHANGE_ENABLED_KEY = "auth.password.force_change_enabled";

    LoginResponse login(LoginRequest request);

    void logout(String accessToken);

    AuthenticatedUser authenticateToken(String accessToken);

    CurrentUserResponse currentUser();

    void changePassword(ChangePasswordRequest request);

    void invalidateUserSessions(Long userId);

    /**
     * 按角色反查持有该角色的用户并逐个失效会话。
     * 权限快照写进了 JWT，改角色权限或改角色菜单授权后不失效旧令牌，收回的权限在令牌过期前（默认 12 小时）继续可用。
     */
    void invalidateUserSessionsByRole(Long roleId);
}
