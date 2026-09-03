package com.jiaopei.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.domain.UserAccountEntity;
import com.jiaopei.platform.auth.dto.ChangePasswordRequest;
import com.jiaopei.platform.auth.mapper.RoleMapper;
import com.jiaopei.platform.auth.mapper.UserAccountMapper;
import com.jiaopei.platform.auth.mapper.UserRoleRelMapper;
import com.jiaopei.platform.auth.mapper.UserTokenMapper;
import com.jiaopei.platform.auth.service.impl.AuthApplicationServiceImpl;
import com.jiaopei.platform.common.exception.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthApplicationServiceImplTest {

    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final AuthApplicationServiceImpl service = new AuthApplicationServiceImpl(
        userAccountMapper,
        mock(RoleMapper.class),
        mock(UserRoleRelMapper.class),
        mock(UserTokenMapper.class),
        passwordEncoder
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
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

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(
            1L,
            1L,
            "admin",
            Set.of("PLATFORM_ADMIN"),
            Set.of("system:user:manage"),
            true
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
