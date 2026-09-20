package com.cornerstone.core.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.dto.ChangePasswordRequest;
import com.cornerstone.core.auth.dto.CurrentUserResponse;
import com.cornerstone.core.auth.dto.LoginRequest;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.security.JwtTokenService;
import com.cornerstone.core.auth.security.UserAccessTokenService;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.auth.service.TokenCacheService;
import com.cornerstone.core.auth.service.impl.AuthApplicationServiceImpl;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.config.properties.CornerstoneProperties;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.core.system.service.SystemMenuService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthApplicationServiceImplTest {

    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final TokenCacheService tokenCacheService = mock(TokenCacheService.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final JwtTokenService jwtTokenService = jwtTokenService();
    private final UserAccessTokenService userAccessTokenService = new UserAccessTokenService(
        userAccountMapper,
        mock(RoleMapper.class),
        mock(UserRoleRelMapper.class),
        tokenCacheService,
        jwtTokenService,
        systemConfigService,
        mock(SystemMenuService.class),
        // 无业务模块注册端判定时，签发结果落回 admin
        List.of()
    );
    private final AuthApplicationServiceImpl service = new AuthApplicationServiceImpl(
        userAccountMapper,
        tokenCacheService,
        jwtTokenService,
        userAccessTokenService,
        passwordEncoder
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginIssuesUniqueTokenPerLoginAndForgedTokenIsRejected() {
        UserAccountEntity user = user();
        user.setStatus("active");
        when(userAccountMapper.selectOne(any())).thenReturn(user);
        when(userAccountMapper.selectById(1L)).thenReturn(user);
        when(systemConfigService.getIntConfig("auth.token.expire_hours", 12)).thenReturn(12);

        LoginResponse first = service.login(new LoginRequest("admin", "Old@123456"));
        LoginResponse second = service.login(new LoginRequest("admin", "Old@123456"));

        // 回归:任何用户登录不得影响其他用户的登录态(历史上所有用户共享同一个固定 token)
        assertNotEquals(first.accessToken(), second.accessToken());

        when(tokenCacheService.getUser(first.accessToken())).thenReturn(
            new AuthenticatedUser(1L, 1L, "admin", Set.of(), Set.of(), false, "admin"));
        assertEquals("admin", service.authenticateToken(first.accessToken()).username());

        assertThrows(BadCredentialsException.class,
            () -> service.authenticateToken(first.accessToken() + "forged"));
    }

    @Test
    void changePasswordRejectsMismatchAndSamePassword() {
        System.out.println(passwordEncoder.encode("Admin@123456"));
        authenticate();
        UserAccountEntity user = user();
        when(userAccountMapper.selectById(1L)).thenReturn(user);

        assertThrows(BusinessException.class, () -> service.changePassword(
            new ChangePasswordRequest("Old@123456", "New@123456", "Mismatch@123456")));
        assertThrows(BusinessException.class, () -> service.changePassword(
            new ChangePasswordRequest("Old@123456", "Old@123456", "Old@123456")));
    }

    @Test
    void changePasswordClearsForceChangeFlag() {
        authenticate();
        UserAccountEntity user = user();
        when(userAccountMapper.selectById(1L)).thenReturn(user);

        assertDoesNotThrow(() -> service.changePassword(
            new ChangePasswordRequest("Old@123456", "New@123456", "New@123456")));

        verify(userAccountMapper).updateById(any(UserAccountEntity.class));
        org.junit.jupiter.api.Assertions.assertFalse(user.getPasswordResetRequired());
        org.junit.jupiter.api.Assertions.assertFalse(user.getForceChangePassword());
        org.junit.jupiter.api.Assertions.assertNotNull(user.getPasswordUpdatedAt());
    }

    @Test
    void currentUserReturnsEffectiveForceChangeWhenSwitchDisabled() {
        authenticate();
        when(userAccountMapper.selectById(1L)).thenReturn(user());
        when(systemConfigService.getBooleanConfig(AuthApplicationService.FORCE_CHANGE_ENABLED_KEY, true))
            .thenReturn(false);

        CurrentUserResponse response = service.currentUser();

        assertFalse(response.forceChangePassword());
    }

    @Test
    void currentUserKeepsForceChangeWhenSwitchEnabled() {
        authenticate();
        when(userAccountMapper.selectById(1L)).thenReturn(user());
        when(systemConfigService.getBooleanConfig(AuthApplicationService.FORCE_CHANGE_ENABLED_KEY, true))
            .thenReturn(true);

        CurrentUserResponse response = service.currentUser();

        assertTrue(response.forceChangePassword());
    }

    private static JwtTokenService jwtTokenService() {
        CornerstoneProperties properties = new CornerstoneProperties();
        properties.getAuth().getJwt().setSecret("0123456789abcdef0123456789abcdef-test-secret");
        return new JwtTokenService(properties);
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(
            1L,
            1L,
            "admin",
            Set.of("PLATFORM_ADMIN"),
            Set.of("system:user:manage"),
            true,
            "admin"
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }

    private UserAccountEntity user() {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash(passwordEncoder.encode("Old@123456"));
        user.setPasswordResetRequired(Boolean.TRUE);
        user.setForceChangePassword(Boolean.TRUE);
        return user;
    }
}
