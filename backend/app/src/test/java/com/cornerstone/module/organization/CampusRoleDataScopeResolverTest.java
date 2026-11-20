package com.cornerstone.module.organization;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.domain.RoleDataScopeEntity;
import com.cornerstone.core.auth.mapper.RoleDataScopeMapper;
import com.cornerstone.core.auth.scope.ResolvedDataScope;
import com.cornerstone.module.organization.service.support.CampusRoleDataScopeResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampusRoleDataScopeResolverTest {

    private static final Long INSTITUTION_ID = 7L;

    private final RoleDataScopeMapper mapper = mock(RoleDataScopeMapper.class);
    private final CampusRoleDataScopeResolver resolver = new CampusRoleDataScopeResolver(mapper);

    @Test
    void noRowsMeansUnrestricted() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertNull(resolver.resolve(INSTITUTION_ID, List.of(1L)));
    }

    @Test
    void explicitAllSentinelMeansUnrestricted() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row(1L, 0L)));

        assertNull(resolver.resolve(INSTITUTION_ID, List.of(1L)));
    }

    @Test
    void singleRoleWhitelistIsRestricted() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row(1L, 11L), row(1L, 12L)));

        ResolvedDataScope scope = resolver.resolve(INSTITUTION_ID, List.of(1L));

        assertTrue(scope.restricted());
        assertEquals("CAMPUS", scope.dimensionKey());
        assertEquals(Set.of(11L, 12L), scope.values());
    }

    @Test
    void multipleRolesAreUnioned() {
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(row(1L, 11L), row(2L, 12L)));

        ResolvedDataScope scope = resolver.resolve(INSTITUTION_ID, List.of(1L, 2L));

        assertEquals(Set.of(11L, 12L), scope.values());
    }

    @Test
    void anyUnconfiguredRoleRelaxesTheWholeUser() {
        // 并集口径：一张"没配置"的牌就是全开，不能只按已配置的角色收窄
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row(1L, 11L)));

        assertNull(resolver.resolve(INSTITUTION_ID, List.of(1L, 2L)));
    }

    @Test
    void emptyRoleListOrInstitutionIsUnrestricted() {
        assertNull(resolver.resolve(INSTITUTION_ID, List.of()));
        assertNull(resolver.resolve(null, List.of(1L)));
    }

    private static RoleDataScopeEntity row(Long roleId, Long scopeValue) {
        RoleDataScopeEntity entity = new RoleDataScopeEntity();
        entity.setRoleId(roleId);
        entity.setInstitutionId(INSTITUTION_ID);
        entity.setScopeType("CAMPUS");
        entity.setScopeValue(scopeValue);
        return entity;
    }
}
