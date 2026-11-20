package com.cornerstone.core.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleDataScopeEntity;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.mapper.RoleDataScopeMapper;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.scope.RoleDataScopeDimension;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.RoleDataScopeUpdate;
import com.cornerstone.core.system.dto.RoleDataScopeView;
import com.cornerstone.core.system.service.impl.RoleDataScopeServiceImpl;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleDataScopeServiceImplTest {

    private static final Long ROLE_ID = 3L;
    private static final Long INSTITUTION_ID = 7L;

    private final RoleDataScopeMapper scopeMapper = mock(RoleDataScopeMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final InstitutionMapper institutionMapper = mock(InstitutionMapper.class);
    private final RoleDataScopeDimension dimension = mock(RoleDataScopeDimension.class);
    private RoleDataScopeServiceImpl service;

    @BeforeEach
    void setUp() {
        when(dimension.dimensionKey()).thenReturn("CAMPUS");
        when(dimension.dimensionLabel()).thenReturn("可访问校区");
        when(dimension.allOptionLabel()).thenReturn("全部校区");
        when(dimension.partOptionLabel()).thenReturn("指定校区");
        when(dimension.options(INSTITUTION_ID)).thenReturn(List.of());
        service = new RoleDataScopeServiceImpl(scopeMapper, roleMapper, institutionMapper, List.of(dimension));
        RoleEntity role = new RoleEntity();
        role.setId(ROLE_ID);
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role);
        authenticate(INSTITUTION_ID, Set.of("ORG_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unconfiguredRoleReadsAsAllMode() {
        when(scopeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<RoleDataScopeView> views = service.list(ROLE_ID, null);

        assertEquals(1, views.size());
        RoleDataScopeView view = views.getFirst();
        assertEquals("ALL", view.scopeMode());
        assertFalse(view.configured());
        assertTrue(view.values().isEmpty());
    }

    @Test
    void allModePersistsOnlyTheSentinelRow() {
        service.replace(ROLE_ID, null, List.of(new RoleDataScopeUpdate("CAMPUS", "ALL", List.of())));

        verify(scopeMapper).physicalDelete(ROLE_ID, INSTITUTION_ID, "CAMPUS");
        ArgumentCaptor<RoleDataScopeEntity> captor = ArgumentCaptor.forClass(RoleDataScopeEntity.class);
        verify(scopeMapper, times(1)).insert(captor.capture());
        assertEquals(0L, captor.getValue().getScopeValue());
        assertEquals(INSTITUTION_ID, captor.getValue().getInstitutionId());
    }

    @Test
    void partModeWritesEachSelectedCampus() {
        service.replace(ROLE_ID, null, List.of(new RoleDataScopeUpdate("CAMPUS", "PART", List.of(11L, 12L, 11L))));

        verify(dimension).validate(INSTITUTION_ID, List.of(11L, 12L));
        ArgumentCaptor<RoleDataScopeEntity> captor = ArgumentCaptor.forClass(RoleDataScopeEntity.class);
        verify(scopeMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(11L, 12L), captor.getAllValues().stream()
            .map(RoleDataScopeEntity::getScopeValue).toList());
    }

    @Test
    void emptyAndSentinelPartValuesAreRejected() {
        assertCode(ResultCode.BAD_REQUEST, () ->
            service.replace(ROLE_ID, null, List.of(new RoleDataScopeUpdate("CAMPUS", "PART", List.of()))));
        assertCode(ResultCode.BAD_REQUEST, () ->
            service.replace(ROLE_ID, null, List.of(new RoleDataScopeUpdate("CAMPUS", "PART", List.of(0L)))));
        assertCode(ResultCode.BAD_REQUEST, () ->
            service.replace(ROLE_ID, null, List.of(new RoleDataScopeUpdate("CAMPUS", "ALL", List.of(11L)))));
    }

    @Test
    void unknownDimensionAndDuplicateDimensionAreRejected() {
        assertCode(ResultCode.BAD_REQUEST, () ->
            service.replace(ROLE_ID, null, List.of(new RoleDataScopeUpdate("REGION", "ALL", List.of()))));
        assertCode(ResultCode.BAD_REQUEST, () -> service.replace(ROLE_ID, null, List.of(
            new RoleDataScopeUpdate("CAMPUS", "ALL", List.of()),
            new RoleDataScopeUpdate("CAMPUS", "PART", List.of(11L)))));
    }

    @Test
    void ordinaryUserCannotConfigureAnotherInstitution() {
        assertCode(ResultCode.FORBIDDEN, () ->
            service.replace(ROLE_ID, 8L, List.of(new RoleDataScopeUpdate("CAMPUS", "ALL", List.of()))));

        verify(scopeMapper, never()).physicalDelete(anyLong(), anyLong(), any());
    }

    @Test
    void platformAdminMayConfigureAnyExistingInstitution() {
        authenticate(INSTITUTION_ID, Set.of("PLATFORM_ADMIN"));
        com.cornerstone.core.tenant.domain.InstitutionEntity institution =
            new com.cornerstone.core.tenant.domain.InstitutionEntity();
        institution.setId(8L);
        when(institutionMapper.selectById(8L)).thenReturn(institution);

        service.replace(ROLE_ID, 8L, List.of(new RoleDataScopeUpdate("CAMPUS", "ALL", List.of())));

        verify(scopeMapper).physicalDelete(eq(ROLE_ID), eq(8L), eq("CAMPUS"));
    }

    private static void assertCode(ResultCode code, org.junit.jupiter.api.function.Executable executable) {
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertEquals(code, exception.getResultCode());
    }

    private static void authenticate(Long institutionId, Set<String> roles) {
        AuthenticatedUser user = new AuthenticatedUser(1L, institutionId, "admin", roles, Set.of(),
            Boolean.FALSE, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
