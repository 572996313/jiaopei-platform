package com.jiaopei.platform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.auth.context.AuthContext;
import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.domain.RoleEntity;
import com.jiaopei.platform.auth.domain.UserAccountEntity;
import com.jiaopei.platform.auth.domain.UserRoleRelEntity;
import com.jiaopei.platform.auth.domain.UserTokenEntity;
import com.jiaopei.platform.auth.dto.ChangePasswordRequest;
import com.jiaopei.platform.auth.dto.CurrentUserResponse;
import com.jiaopei.platform.auth.dto.LoginRequest;
import com.jiaopei.platform.auth.dto.LoginResponse;
import com.jiaopei.platform.auth.mapper.RoleMapper;
import com.jiaopei.platform.auth.mapper.UserAccountMapper;
import com.jiaopei.platform.auth.mapper.UserRoleRelMapper;
import com.jiaopei.platform.auth.mapper.UserTokenMapper;
import com.jiaopei.platform.auth.service.AuthApplicationService;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationServiceImpl implements AuthApplicationService {

    private static final String ACTIVE = "active";
    private static final int TOKEN_BYTES = 32;
    private static final long TOKEN_HOURS = 12L;

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final UserTokenMapper userTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthApplicationServiceImpl(UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                      UserRoleRelMapper userRoleRelMapper, UserTokenMapper userTokenMapper,
                                      PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.userTokenMapper = userTokenMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccountEntity user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
            .eq(UserAccountEntity::getUsername, normalize(request.username())));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("User account is disabled");
        }

        RolePermissionSnapshot snapshot = loadRolePermissionSnapshot(user.getId());
        String accessToken = generateToken();
        UserTokenEntity token = new UserTokenEntity();
        token.setUserId(user.getId());
        token.setAccessToken(accessToken);
        token.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_HOURS));
        token.setDeleted(Boolean.FALSE);
        userTokenMapper.insert(token);

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
            Boolean.TRUE.equals(user.getForceChangePassword())
        );
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        UserTokenEntity token = findToken(accessToken);
        if (token != null && token.getRevokedAt() == null) {
            token.setRevokedAt(LocalDateTime.now());
            userTokenMapper.updateById(token);
        }
    }

    @Override
    public AuthenticatedUser authenticateToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadCredentialsException("Access token is missing");
        }
        UserTokenEntity token = findToken(accessToken);
        if (token == null || token.getRevokedAt() != null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Access token is invalid or expired");
        }
        UserAccountEntity user = requireUser(token.getUserId());
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("User account is disabled");
        }
        RolePermissionSnapshot snapshot = loadRolePermissionSnapshot(user.getId());
        return new AuthenticatedUser(
            user.getId(),
            user.getInstitutionId(),
            user.getUsername(),
            snapshot.roleCodes(),
            snapshot.permissionCodes(),
            Boolean.TRUE.equals(user.getForceChangePassword())
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
            user.getDefaultCampusId(),
            user.getUsername(),
            user.getDisplayName(),
            new ArrayList<>(authenticatedUser.roles()),
            new ArrayList<>(authenticatedUser.permissions()),
            user.getPasswordResetRequired(),
            Boolean.TRUE.equals(user.getForceChangePassword())
        );
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AuthenticatedUser authenticatedUser = AuthContext.requireUser();
        UserAccountEntity user = requireUser(authenticatedUser.userId());
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "New password and confirmation do not match");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Old password is invalid");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "New password cannot be the same as old password");
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
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authenticated user no longer exists");
        }
        return user;
    }

    private UserTokenEntity findToken(String accessToken) {
        return userTokenMapper.selectOne(new LambdaQueryWrapper<UserTokenEntity>()
            .eq(UserTokenEntity::getAccessToken, accessToken));
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
        for (RoleEntity role : roles) {
            roleCodes.add(role.getRoleCode());
            permissionCodes.addAll(splitPermissionCodes(role.getPermissionCodes()));
        }
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

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
