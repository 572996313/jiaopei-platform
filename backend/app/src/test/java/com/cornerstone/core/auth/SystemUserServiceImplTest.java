package com.cornerstone.core.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.UserRoleUpdateRequest;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.auth.service.impl.SystemUserServiceImpl;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemUserServiceImplTest {

    @Test
    void repeatedRoleSaveUsesPhysicalDeleteBeforeReinsert() {
        UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        UserRoleRelMapper userRoleRelMapper = mock(UserRoleRelMapper.class);
        InstitutionMapper institutionMapper = mock(InstitutionMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SystemUserServiceImpl service = new SystemUserServiceImpl(
            userAccountMapper,
            roleMapper,
            userRoleRelMapper,
            institutionMapper,
            passwordEncoder,
            mock(AuthApplicationService.class)
        );

        UserAccountEntity user = new UserAccountEntity();
        user.setId(1L);
        user.setInstitutionId(1L);
        user.setUsername("admin");
        user.setDisplayName("Admin");
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
    }
}
