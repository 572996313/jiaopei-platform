package com.cornerstone.core.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.ChangePasswordRequest;
import com.cornerstone.core.auth.dto.CurrentUserResponse;
import com.cornerstone.core.auth.dto.LoginRequest;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.security.JwtTokenService;
import com.cornerstone.core.auth.security.UserAccessTokenService;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.auth.service.TokenCacheService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationServiceImpl implements AuthApplicationService {

    private static final String ACTIVE = "active";

    private final UserAccountMapper userAccountMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final TokenCacheService tokenCacheService;
    private final JwtTokenService jwtTokenService;
    private final UserAccessTokenService userAccessTokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthApplicationServiceImpl(UserAccountMapper userAccountMapper, UserRoleRelMapper userRoleRelMapper,
                                      TokenCacheService tokenCacheService,
                                      JwtTokenService jwtTokenService, UserAccessTokenService userAccessTokenService,
                                      PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.tokenCacheService = tokenCacheService;
        this.jwtTokenService = jwtTokenService;
        this.userAccessTokenService = userAccessTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccountEntity user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
            .eq(UserAccountEntity::getUsername, normalize(request.username())));
        // 小程序自助注册的用户没有密码,走账号密码登录一律视为凭证错误
        if (user == null || user.getPasswordHash() == null
            || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }
        return userAccessTokenService.issue(user);
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
        // 强制改密标记是账号的实时状态,不是登录时固化的权限快照:
        // 自助改密会清掉库里的标记,但 Redis 会话里仍留着登录那一刻的 true。
        // 若直接返回快照,改密后同一个 token 会被过滤器一直当成"待改密"拦成 403。
        // 这里已经查过库,顺带用库里的值覆盖,避免再为改密专门改写会话缓存。
        return new AuthenticatedUser(
            user.userId(),
            user.orgId(),
            user.username(),
            user.roles(),
            user.permissions(),
            Boolean.TRUE.equals(userAccount.getForceChangePassword()),
            user.portal(),
            // 数据范围是登录快照的一部分，这里每次请求都会重新构造 principal，漏传会让范围在第二次请求起丢失
            user.dataScopeKey(),
            user.dataScopeIds()
        );
    }

    @Override
    public CurrentUserResponse currentUser() {
        AuthenticatedUser authenticatedUser = AuthContext.requireUser();
        UserAccountEntity user = requireUser(authenticatedUser.userId());
        return new CurrentUserResponse(
            user.getId(),
            user.getInstitutionId(),
            user.getInstitutionId(),
            user.getUsername(),
            user.getDisplayName(),
            new ArrayList<>(authenticatedUser.roles()),
            new ArrayList<>(authenticatedUser.permissions()),
            user.getPasswordResetRequired(),
            userAccessTokenService.effectiveForceChangePassword(user),
            authenticatedUser.portal(),
            authenticatedUser.dataScopeKey(),
            authenticatedUser.dataScopeIds() == null
                ? null
                : new ArrayList<>(authenticatedUser.dataScopeIds())
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

    private UserAccountEntity requireUser(Long userId) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前登录用户已不存在");
        }
        return user;
    }

    @Override
    @Transactional
    public void updateDisplayName(Long userId, String displayName) {
        String value = normalize(displayName);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "显示名称不能为空");
        }
        if (value.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "显示名称长度不能超过 " + MAX_DISPLAY_NAME_LENGTH + " 个字符");
        }
        UserAccountEntity user = requireUser(userId);
        if (value.equals(user.getDisplayName())) {
            return;
        }
        user.setDisplayName(value);
        userAccountMapper.updateById(user);
    }

    @Override
    public void invalidateUserSessions(Long userId) {
        tokenCacheService.invalidateUserTokens(userId);
    }

    @Override
    public void invalidateUserSessionsByRole(Long roleId) {
        userRoleRelMapper.selectList(new LambdaQueryWrapper<UserRoleRelEntity>()
                .eq(UserRoleRelEntity::getRoleId, roleId))
            .stream()
            .map(UserRoleRelEntity::getUserId)
            .distinct()
            .forEach(this::invalidateUserSessions);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
