package com.cornerstone.core.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.UserRoleUpdateRequest;
import com.cornerstone.core.auth.event.UserAccountDeregisteredEvent;
import com.cornerstone.core.auth.mapper.AuthInvitationMapper;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.auth.service.impl.SystemUserServiceImpl;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemUserServiceImplTest {

    private UserAccountMapper userAccountMapper;
    private RoleMapper roleMapper;
    private UserRoleRelMapper userRoleRelMapper;
    private InstitutionMapper institutionMapper;
    private AuthInvitationMapper invitationMapper;
    private AuthApplicationService authApplicationService;
    private ApplicationEventPublisher eventPublisher;
    private SystemUserServiceImpl service;

    @BeforeEach
    void setUp() {
        userAccountMapper = mock(UserAccountMapper.class);
        roleMapper = mock(RoleMapper.class);
        userRoleRelMapper = mock(UserRoleRelMapper.class);
        institutionMapper = mock(InstitutionMapper.class);
        invitationMapper = mock(AuthInvitationMapper.class);
        authApplicationService = mock(AuthApplicationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new SystemUserServiceImpl(userAccountMapper, roleMapper, userRoleRelMapper,
            institutionMapper, invitationMapper, mock(PasswordEncoder.class),
            authApplicationService, eventPublisher);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void repeatedRoleSaveUsesPhysicalDeleteBeforeReinsert() {
        UserAccountEntity user = account(1L, 1L, "admin", "active");
        when(userAccountMapper.selectById(1L)).thenReturn(user);

        RoleEntity role = new RoleEntity();
        role.setId(10L);
        role.setRoleCode("PLATFORM_ADMIN");
        when(roleMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(role));

        UserRoleRelEntity relation = new UserRoleRelEntity();
        relation.setUserId(1L);
        relation.setRoleId(10L);
        when(userRoleRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(relation));

        UserRoleUpdateRequest request = new UserRoleUpdateRequest(List.of(10L));
        assertDoesNotThrow(() -> service.updateRoles(1L, request));
        assertDoesNotThrow(() -> service.updateRoles(1L, request));

        verify(userRoleRelMapper, times(2)).physicalDeleteByUserId(1L);
        verify(userRoleRelMapper, times(2)).insert(any(UserRoleRelEntity.class));
        // 回归:角色快照写进了 JWT，重排角色后必须让该用户重新登录
        verify(authApplicationService, times(2)).invalidateUserSessions(1L);
    }

    @Test
    void deregisterClearsCredentialAndRoleRowsThenDeletesAccount() {
        authenticate(1L, 1L, Set.of("PLATFORM_ADMIN"));
        when(userAccountMapper.selectById(9L)).thenReturn(account(9L, 1L, "ops01", "disabled"));

        service.deregisterUser(9L);

        // 会话先失效:账号行删掉后,残留令牌里的权限快照无人可复核
        verify(authApplicationService).invalidateUserSessions(9L);
        // 激活码的 user_id 外键指向账号行,不清则物理删被 1451 拒掉
        verify(invitationMapper).physicalDeleteByUserId(9L);
        verify(userRoleRelMapper).physicalDeleteByUserId(9L);
        verify(eventPublisher).publishEvent(new UserAccountDeregisteredEvent(9L));
        verify(userAccountMapper).physicalDeleteById(9L);
    }

    @Test
    void deregisterRejectsActiveAccount() {
        authenticate(1L, 1L, Set.of("PLATFORM_ADMIN"));
        when(userAccountMapper.selectById(9L)).thenReturn(account(9L, 1L, "ops01", "active"));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.deregisterUser(9L));

        assertTrue(exception.getMessage().contains("请先停用"));
        verify(userAccountMapper, never()).physicalDeleteById(anyLong());
    }

    @Test
    void deregisterRejectsCurrentUserHimself() {
        authenticate(1L, 1L, Set.of("PLATFORM_ADMIN"));
        when(userAccountMapper.selectById(1L)).thenReturn(account(1L, 1L, "admin", "disabled"));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.deregisterUser(1L));

        assertTrue(exception.getMessage().contains("不能注销当前登录的账号"));
        verify(userAccountMapper, never()).physicalDeleteById(anyLong());
    }

    @Test
    void deregisterRejectsAccountOfAnotherInstitution() {
        // 机构管理员(非平台角色)拿别人的账号 ID 也删不动:注销入参只是筛选条件
        authenticate(2L, 1L, Set.of("INSTITUTION_ADMIN"));
        when(userAccountMapper.selectById(9L)).thenReturn(account(9L, 2L, "other-org", "disabled"));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.deregisterUser(9L));

        assertTrue(exception.getMessage().contains("只能注销本机构的账号"));
        verify(userAccountMapper, never()).physicalDeleteById(anyLong());
    }

    @Test
    void deregisterRejectsLastActivePlatformAdmin() {
        authenticate(1L, 1L, Set.of("PLATFORM_ADMIN"));
        when(userAccountMapper.selectById(9L)).thenReturn(account(9L, 1L, "ops01", "disabled"));

        RoleEntity platformRole = new RoleEntity();
        platformRole.setId(10L);
        platformRole.setRoleCode("PLATFORM_ADMIN");
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(platformRole);
        // 目标账号持有平台管理员角色,且别的持有者没有一个还在启用中
        when(userRoleRelMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(userRoleRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.deregisterUser(9L));

        assertTrue(exception.getMessage().contains("最后一个可用的平台管理员"));
        verify(userAccountMapper, never()).physicalDeleteById(anyLong());
    }

    private UserAccountEntity account(Long id, Long institutionId, String username, String status) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setId(id);
        entity.setInstitutionId(institutionId);
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setStatus(status);
        return entity;
    }

    private void authenticate(Long userId, Long orgId, Set<String> roles) {
        AuthenticatedUser user = new AuthenticatedUser(userId, orgId, "operator",
            roles, Set.of("system:user:manage"), false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }
}
