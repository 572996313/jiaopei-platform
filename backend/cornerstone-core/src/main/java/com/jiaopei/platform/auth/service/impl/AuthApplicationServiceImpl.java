package com.jiaopei.platform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.auth.context.AuthContext;
import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.domain.UserAccountEntity;
import com.jiaopei.platform.auth.dto.ChangePasswordRequest;
import com.jiaopei.platform.auth.dto.CurrentUserResponse;
import com.jiaopei.platform.auth.dto.LoginRequest;
import com.jiaopei.platform.auth.dto.LoginResponse;
import com.jiaopei.platform.auth.mapper.UserAccountMapper;
import com.jiaopei.platform.auth.security.JwtTokenService;
import com.jiaopei.platform.auth.security.UserAccessTokenService;
import com.jiaopei.platform.auth.service.AuthApplicationService;
import com.jiaopei.platform.auth.service.TokenCacheService;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
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
    private final TokenCacheService tokenCacheService;
    private final JwtTokenService jwtTokenService;
    private final UserAccessTokenService userAccessTokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthApplicationServiceImpl(UserAccountMapper userAccountMapper, TokenCacheService tokenCacheService,
                                      JwtTokenService jwtTokenService, UserAccessTokenService userAccessTokenService,
                                      PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
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
            userAccessTokenService.effectiveForceChangePassword(user),
            authenticatedUser.portal()
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

    public void invalidateUserSessions(Long userId) {
        tokenCacheService.invalidateUserTokens(userId);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
