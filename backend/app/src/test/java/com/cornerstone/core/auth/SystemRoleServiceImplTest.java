package com.cornerstone.core.auth;

import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.dto.RoleCreateRequest;
import com.cornerstone.core.auth.dto.RoleUpdateRequest;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.auth.service.impl.SystemRoleServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemRoleServiceImplTest {

    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final AuthApplicationService authApplicationService = mock(AuthApplicationService.class);
    private final SystemRoleServiceImpl service =
        new SystemRoleServiceImpl(roleMapper, authApplicationService);

    @Test
    void updateRoleInvalidatesSessionsOfEveryHolder() {
        when(roleMapper.selectById(7L)).thenReturn(role(7L));

        assertDoesNotThrow(() -> service.updateRole(7L,
            new RoleUpdateRequest("应用管理员", List.of("tenant:read"), "active", null)));

        verify(roleMapper).updateById(any(RoleEntity.class));
        // 回归:权限码与状态都写进了持有者令牌，改完必须按角色反查踢掉在线会话
        verify(authApplicationService).invalidateUserSessionsByRole(7L);
    }

    @Test
    void createRoleDoesNotInvalidateAnySession() {
        when(roleMapper.selectCount(any())).thenReturn(0L);

        assertDoesNotThrow(() -> service.createRole(new RoleCreateRequest(
            "NEW_ROLE", "新角色", List.of("tenant:read"), "active", null)));

        verify(authApplicationService, never()).invalidateUserSessionsByRole(any());
    }

    private static RoleEntity role(Long id) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleCode("PLATFORM_ADMIN");
        role.setRoleName("平台管理员");
        role.setPermissionCodes("tenant:read");
        role.setStatus("active");
        return role;
    }
}
