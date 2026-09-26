package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.dto.ChangePasswordRequest;
import com.cornerstone.core.auth.dto.CurrentUserResponse;
import com.cornerstone.core.auth.dto.LoginRequest;
import com.cornerstone.core.auth.dto.LoginResponse;

public interface AuthApplicationService {

    String FORCE_CHANGE_ENABLED_KEY = "auth.password.force_change_enabled";

    /** 显示名称列宽上限，与 sys_user_account.display_name 的 varchar(128) 一致 */
    int MAX_DISPLAY_NAME_LENGTH = 128;

    LoginResponse login(LoginRequest request);

    void logout(String accessToken);

    AuthenticatedUser authenticateToken(String accessToken);

    CurrentUserResponse currentUser();

    void changePassword(ChangePasswordRequest request);

    /**
     * 回写账号显示名，供业务模块在档案关联建立后把"我是谁"同步到账号上。
     *
     * <p>显示名不在 JWT 也不在 Redis 会话里（{@code AuthenticatedUser} 只有 username），
     * {@code /api/auth/me} 每次现读库，所以改完不必失效会话。</p>
     */
    void updateDisplayName(Long userId, String displayName);

    void invalidateUserSessions(Long userId);

    /**
     * 按角色反查持有该角色的用户并逐个失效会话。
     * 权限快照写进了 JWT，改角色权限或改角色菜单授权后不失效旧令牌，收回的权限在令牌过期前（默认 12 小时）继续可用。
     */
    void invalidateUserSessionsByRole(Long roleId);
}
