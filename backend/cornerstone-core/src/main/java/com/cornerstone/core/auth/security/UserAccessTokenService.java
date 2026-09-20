package com.cornerstone.core.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.auth.service.PortalResolver;
import com.cornerstone.core.auth.service.TokenCacheService;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.core.system.service.SystemMenuService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 为已通过身份校验的用户统一签发访问令牌(账号密码登录与微信小程序登录共用):
 * 加载角色权限快照 → 签发 JWT 并写入 Redis 会话 → 更新最近登录时间 → 组装登录响应。
 */
@Component
public class UserAccessTokenService {

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final TokenCacheService tokenCacheService;
    private final JwtTokenService jwtTokenService;
    private final SystemConfigService systemConfigService;
    private final SystemMenuService systemMenuService;
    /** 各业务模块提供的端判定，按装配顺序取第一个非空结果 */
    private final List<PortalResolver> portalResolvers;

    public UserAccessTokenService(UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                  UserRoleRelMapper userRoleRelMapper, TokenCacheService tokenCacheService,
                                  JwtTokenService jwtTokenService, SystemConfigService systemConfigService,
                                  SystemMenuService systemMenuService,
                                  List<PortalResolver> portalResolvers) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.tokenCacheService = tokenCacheService;
        this.jwtTokenService = jwtTokenService;
        this.systemConfigService = systemConfigService;
        this.systemMenuService = systemMenuService;
        this.portalResolvers = portalResolvers;
    }

    public LoginResponse issue(UserAccountEntity user) {
        RolePermissionSnapshot snapshot = loadRolePermissionSnapshot(user.getId());

        Integer tokenExpireHours = systemConfigService.getIntConfig("auth.token.expire_hours", 12);
        String portal = resolvePortal(user.getRegisterSource(), snapshot.roleCodes());
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
            user.getId(),
            user.getInstitutionId(),
            user.getUsername(),
            snapshot.roleCodes(),
            snapshot.permissionCodes(),
            Boolean.TRUE.equals(user.getForceChangePassword()),
            portal
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
            effectiveForceChangePassword(user),
            portal
        );
    }

    /**
     * 端归属:端是比角色高一级的维度,决定该用户看到哪一套功能清单(菜单树)。
     * 底座只认识 admin,具体归属交给各业务模块注册的 {@link PortalResolver}。
     */
    private String resolvePortal(String registerSource, Set<String> roleCodes) {
        for (PortalResolver resolver : portalResolvers) {
            String portal = resolver.resolve(registerSource, roleCodes);
            if (portal != null && !portal.isBlank()) {
                return portal;
            }
        }
        return PortalResolver.ADMIN;
    }

    /**
     * 对外返回的"需强制改密"为生效值：用户标志记录初始密码未轮换这一事实，
     * 总开关 auth.password.force_change_enabled 只控制是否执行拦截。
     */
    public boolean effectiveForceChangePassword(UserAccountEntity user) {
        Boolean enabled = systemConfigService.getBooleanConfig(AuthApplicationService.FORCE_CHANGE_ENABLED_KEY, true);
        return Boolean.TRUE.equals(enabled) && Boolean.TRUE.equals(user.getForceChangePassword());
    }

    public record RolePermissionSnapshot(
        Set<String> roleCodes,
        Set<String> permissionCodes
    ) {
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
            .filter(role -> "active".equalsIgnoreCase(role.getStatus()))
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
}
