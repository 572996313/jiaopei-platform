package com.jiaopei.platform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.auth.context.AuthContext;
import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.domain.RoleEntity;
import com.jiaopei.platform.auth.domain.UserAccountEntity;
import com.jiaopei.platform.auth.domain.UserRoleRelEntity;
import com.jiaopei.platform.auth.dto.ChangePasswordRequest;
import com.jiaopei.platform.auth.dto.CurrentUserResponse;
import com.jiaopei.platform.auth.dto.LoginRequest;
import com.jiaopei.platform.auth.dto.LoginResponse;
import com.jiaopei.platform.auth.mapper.RoleMapper;
import com.jiaopei.platform.auth.mapper.UserAccountMapper;
import com.jiaopei.platform.auth.mapper.UserRoleRelMapper;
import com.jiaopei.platform.auth.security.JwtTokenService;
import com.jiaopei.platform.auth.service.AuthApplicationService;
import com.jiaopei.platform.auth.service.TokenCacheService;
import com.jiaopei.platform.system.service.SystemConfigService;
import com.jiaopei.platform.system.service.SystemMenuService;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationServiceImpl implements AuthApplicationService {

    private static final String ACTIVE = "active";

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final TokenCacheService tokenCacheService;
    private final JwtTokenService jwtTokenService;
    private final SystemConfigService systemConfigService;
    private final SystemMenuService systemMenuService;
    private final PasswordEncoder passwordEncoder;

    public AuthApplicationServiceImpl(UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                      UserRoleRelMapper userRoleRelMapper, TokenCacheService tokenCacheService,
                                      JwtTokenService jwtTokenService, SystemConfigService systemConfigService,
                                      SystemMenuService systemMenuService, PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.tokenCacheService = tokenCacheService;
        this.jwtTokenService = jwtTokenService;
        this.systemConfigService = systemConfigService;
        this.systemMenuService = systemMenuService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccountEntity user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
            .eq(UserAccountEntity::getUsername, normalize(request.username())));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }

        RolePermissionSnapshot snapshot = loadRolePermissionSnapshot(user.getId());

        Integer tokenExpireHours = systemConfigService.getIntConfig("auth.token.expire_hours", 12);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
            user.getId(),
            user.getInstitutionId(),
            user.getUsername(),
            snapshot.roleCodes(),
            snapshot.permissionCodes(),
            Boolean.TRUE.equals(user.getForceChangePassword())
        );
        String accessToken = jwtTokenService.issue(authenticatedUser, tokenExpireHours, TimeUnit.HOURS);
        tokenCacheService.storeToken(accessToken, authenticatedUser, tokenExpireHours, TimeUnit.HOURS);

        user.setLastLoginAt(LocalDateTime.now());
        userAccountMapper.updateById(user);

        return new LoginResponse(
            user.getId(),
            user.getInstitutionId(),
            user.getUsername(),
            user.getDisplayName(),
            new ArrayList<>(snapshot.roleCodes()),
            new ArrayList<>(snapshot.permissionCodes()),
            "Bearer",
            accessToken,
            effectiveForceChangePassword(user)
        );
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        tokenCacheService.removeToken(accessToken);
    }

    @Override
    public AuthenticatedUser authenticateToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadCredentialsException("访问令牌缺失");
        }
        // 先校验 JWT 签名与过期时间,再查 Redis 会话(登出/踢人后立即失效)
        jwtTokenService.verify(accessToken);
        AuthenticatedUser user = tokenCacheService.getUser(accessToken);
        if (user == null) {
            throw new BadCredentialsException("访问令牌无效或已过期");
        }
        UserAccountEntity userAccount = requireUser(user.userId());
        if (!ACTIVE.equalsIgnoreCase(userAccount.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }
        return user;
    }

    @Override
    public CurrentUserResponse currentUser() {
        AuthenticatedUser authenticatedUser = AuthContext.requireUser();
        UserAccountEntity user = requireUser(authenticatedUser.userId());
        return new CurrentUserResponse(
            user.getId(),
            user.getInstitutionId(),
            user.getInstitutionId(),
            user.getDefaultCampusId(),
            user.getUsername(),
            user.getDisplayName(),
            new ArrayList<>(authenticatedUser.roles()),
            new ArrayList<>(authenticatedUser.permissions()),
            user.getPasswordResetRequired(),
            effectiveForceChangePassword(user)
        );
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AuthenticatedUser authenticatedUser = AuthContext.requireUser();
        UserAccountEntity user = requireUser(authenticatedUser.userId());
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码与确认密码不一致");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetRequired(Boolean.FALSE);
        user.setForceChangePassword(Boolean.FALSE);
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userAccountMapper.updateById(user);
    }

    /**
     * 对外返回的"需强制改密"为生效值：用户标志记录初始密码未轮换这一事实，
     * 总开关 auth.password.force_change_enabled 只控制是否执行拦截。
     */
    private boolean effectiveForceChangePassword(UserAccountEntity user) {
        Boolean enabled = systemConfigService.getBooleanConfig(AuthApplicationService.FORCE_CHANGE_ENABLED_KEY, true);
        return Boolean.TRUE.equals(enabled) && Boolean.TRUE.equals(user.getForceChangePassword());
    }

    private UserAccountEntity requireUser(Long userId) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前登录用户已不存在");
        }
        return user;
    }

    public void invalidateUserSessions(Long userId) {
        tokenCacheService.invalidateUserTokens(userId);
    }

    private RolePermissionSnapshot loadRolePermissionSnapshot(Long userId) {
        List<UserRoleRelEntity> relations = userRoleRelMapper.selectList(new LambdaQueryWrapper<UserRoleRelEntity>()
            .eq(UserRoleRelEntity::getUserId, userId));
        if (relations.isEmpty()) {
            return new RolePermissionSnapshot(Set.of(), Set.of());
        }
        List<Long> roleIds = relations.stream()
            .map(UserRoleRelEntity::getRoleId)
            .distinct()
            .toList();
        List<RoleEntity> roles = roleMapper.selectBatchIds(roleIds).stream()
            .filter(role -> ACTIVE.equalsIgnoreCase(role.getStatus()))
            .toList();
        Set<String> roleCodes = new LinkedHashSet<>();
        Set<String> permissionCodes = new LinkedHashSet<>();
        List<Long> activeRoleIds = new ArrayList<>();
        for (RoleEntity role : roles) {
            roleCodes.add(role.getRoleCode());
            activeRoleIds.add(role.getId());
            permissionCodes.addAll(splitPermissionCodes(role.getPermissionCodes()));
        }
        // 按钮权限模型：角色勾选的菜单/按钮节点上的权限码与角色 CSV 码取并集
        permissionCodes.addAll(systemMenuService.getPermissionCodesByRoleIds(activeRoleIds));
        return new RolePermissionSnapshot(roleCodes, permissionCodes);
    }

    private List<String> splitPermissionCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private record RolePermissionSnapshot(
        Set<String> roleCodes,
        Set<String> permissionCodes
    ) {
    }
}
