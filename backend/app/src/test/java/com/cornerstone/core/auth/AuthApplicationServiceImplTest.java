package com.cornerstone.core.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthApplicationServiceImplTest {

    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final UserRoleRelMapper userRoleRelMapper = mock(UserRoleRelMapper.class);
    private final TokenCacheService tokenCacheService = mock(TokenCacheService.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final JwtTokenService jwtTokenService = jwtTokenService();
    private final UserAccessTokenService userAccessTokenService = new UserAccessTokenService(
        userAccountMapper,
        roleMapper,
        userRoleRelMapper,
        tokenCacheService,
        jwtTokenService,
        systemConfigService,
        mock(SystemMenuService.class),
        // 无业务模块注册端判定时，签发结果落回 admin
        List.of(),
        // 无业务模块注册数据范围时，登录快照不限制
        List.of()
    );
    private final AuthApplicationServiceImpl service = new AuthApplicationServiceImpl(
        userAccountMapper,
        roleMapper,
        userRoleRelMapper,
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
    void authenticateTokenPreservesDataScopeFromSessionSnapshot() {
        // 回归:每个请求都会用 7 参重新 new AuthenticatedUser，漏传范围字段会表现为
        //「登录后受限，翻页/刷新又变全部」
        UserAccountEntity user = user();
        user.setStatus("active");
        when(userAccountMapper.selectById(1L)).thenReturn(user);
        AuthenticatedUser scoped = new AuthenticatedUser(1L, 1L, "admin", Set.of("ORG_ADMIN"), Set.of(),
            Boolean.FALSE, "admin", "CAMPUS", Set.of(11L, 12L));
        String token = jwtTokenService.issue(scoped, 12, TimeUnit.HOURS);
        when(tokenCacheService.getUser(token)).thenReturn(scoped);

        AuthenticatedUser authenticated = service.authenticateToken(token);

        assertEquals("CAMPUS", authenticated.dataScopeKey());
        assertEquals(Set.of(11L, 12L), authenticated.dataScopeIds());
    }

    /**
     * 回归:强制改密标记以库为准,不能被登录时写进 Redis 的旧快照覆盖。
     * 改密只清了库里的标记,如果认证还返回快照里的 true,同一 token 之后会被过滤器一直拦成 403,
     * 表现就是"改完密码必须退出重登才正常"。
     */
    @Test
    void authenticateTokenUsesLiveForceChangeFlagInsteadOfLoginSnapshot() {
        UserAccountEntity user = user();
        user.setStatus("active");
        when(userAccountMapper.selectById(1L)).thenReturn(user);
        String token = jwtTokenService.issue(
            new AuthenticatedUser(1L, 1L, "admin", Set.of(), Set.of(), true, "admin"), 12, TimeUnit.HOURS);
        when(tokenCacheService.getUser(token)).thenReturn(
            new AuthenticatedUser(1L, 1L, "admin", Set.of(), Set.of(), true, "admin"));

        user.setForceChangePassword(Boolean.FALSE);
        assertFalse(service.authenticateToken(token).forceChangePassword());

        user.setForceChangePassword(Boolean.TRUE);
        assertTrue(service.authenticateToken(token).forceChangePassword());
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

    /** 业务模块绑定档案后回写显示名：超列宽直接拒绝，而不是把整句塞进 varchar(128) */
    @Test
    void updateDisplayNameWritesAndRejectsTooLong() {
        UserAccountEntity user = user();
        when(userAccountMapper.selectById(1L)).thenReturn(user);

        service.updateDisplayName(1L, " 小明家长 ");

        assertEquals("小明家长", user.getDisplayName());
        assertThrows(BusinessException.class,
            () -> service.updateDisplayName(1L, "名".repeat(AuthApplicationService.MAX_DISPLAY_NAME_LENGTH + 1)));
        assertThrows(BusinessException.class, () -> service.updateDisplayName(1L, "   "));
    }

    /** 名字没变就不写库：档案名常改、账号名跟着改，重复绑定不应产生空更新 */
    @Test
    void updateDisplayNameSkipsWriteWhenUnchanged() {
        UserAccountEntity user = user();
        user.setDisplayName("小明家长");
        when(userAccountMapper.selectById(1L)).thenReturn(user);

        service.updateDisplayName(1L, " 小明家长 ");

        verify(userAccountMapper, never()).updateById(any(UserAccountEntity.class));
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

    @Test
    void currentUserCarriesProfileFieldsAndResolvesRoleNames() {
        authenticate();
        UserAccountEntity user = user();
        user.setPhone("13800000000");
        user.setLastLoginAt(LocalDateTime.of(2026, 11, 22, 9, 30));
        user.setPasswordUpdatedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        when(userAccountMapper.selectById(1L)).thenReturn(user);
        RoleEntity role = new RoleEntity();
        role.setRoleCode("PLATFORM_ADMIN");
        role.setRoleName("平台管理员");
        when(roleMapper.selectList(any())).thenReturn(List.of(role));

        CurrentUserResponse response = service.currentUser();

        assertEquals("13800000000", response.phone());
        assertEquals(user.getLastLoginAt(), response.lastLoginAt());
        assertEquals(user.getPasswordUpdatedAt(), response.passwordUpdatedAt());
        assertEquals(List.of("平台管理员"), response.roleNames());
    }

    @Test
    void currentUserFallsBackToRoleCodeWhenRoleNameIsMissing() {
        authenticate();
        when(userAccountMapper.selectById(1L)).thenReturn(user());
        when(roleMapper.selectList(any())).thenReturn(List.of());

        CurrentUserResponse response = service.currentUser();

        // 角色表查不到名称时展示角色码，不能因为一条名称缺失让整个 /auth/me 失败
        assertEquals(List.of("PLATFORM_ADMIN"), response.roleNames());
    }

    @Test
    void invalidateUserSessionsByRoleKicksEveryHolderOfTheRole() {
        when(userRoleRelMapper.selectList(any())).thenReturn(List.of(
            userRoleRel(11L, 7L), userRoleRel(12L, 7L), userRoleRel(11L, 8L)));

        service.invalidateUserSessionsByRole(7L);

        verify(tokenCacheService).invalidateUserTokens(11L);
        verify(tokenCacheService).invalidateUserTokens(12L);
        verify(tokenCacheService, never()).invalidateUserTokens(13L);
    }

    @Test
    void invalidateUserSessionsByRoleWithoutHoldersIsANoOp() {
        when(userRoleRelMapper.selectList(any())).thenReturn(List.of());

        service.invalidateUserSessionsByRole(7L);

        verify(tokenCacheService, never()).invalidateUserTokens(any());
    }

    private static UserRoleRelEntity userRoleRel(Long userId, Long roleId) {
        UserRoleRelEntity relation = new UserRoleRelEntity();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
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
