package com.cornerstone.core.tenant;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.dto.CurrentInstitutionUpdateRequest;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.core.tenant.service.impl.InstitutionServiceImpl;
import com.cornerstone.support.CodeRuleTestSupport;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstitutionServiceImplTest {

    private final InstitutionMapper institutionMapper = mock(InstitutionMapper.class);
    private final InstitutionServiceImpl service = new InstitutionServiceImpl(institutionMapper, CodeRuleTestSupport.unconfiguredRules());

    @BeforeAll
    static void initMybatisTableInfo() {
        // 纯 Mockito 单测没有 MyBatis 容器，需手动初始化实体表信息，
        // 否则捕获的 LambdaQueryWrapper 在 getSqlSegment() 时找不到 lambda 缓存。
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), InstitutionEntity.class);
    }

    @Test
    void updateCurrentInstitutionWritesAllowedProfileFieldsOnly() {
        InstitutionEntity entity = new InstitutionEntity();
        entity.setId(1L);
        entity.setOrgCode("SYS-DEFAULT");
        entity.setOrgName("系统默认机构");
        entity.setStatus("active");
        when(institutionMapper.selectById(1L)).thenReturn(entity);
        when(institutionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        var view = service.updateCurrentInstitution(1L, new CurrentInstitutionUpdateRequest(
            "新机构名称",
            "新简称",
            "张老师",
            "13900000000",
            "新地址",
            "active",
            "更新备注"
        ));

        assertEquals("SYS-DEFAULT", view.orgCode());
        assertEquals("新机构名称", view.name());
        assertEquals("新简称", view.shortName());
        assertEquals("张老师", view.contactName());
        verify(institutionMapper).updateById(entity);
    }

    @Test
    void updateCurrentInstitutionRejectsBlankName() {
        InstitutionEntity entity = new InstitutionEntity();
        entity.setId(1L);
        entity.setOrgCode("SYS-DEFAULT");
        entity.setOrgName("系统默认机构");
        when(institutionMapper.selectById(1L)).thenReturn(entity);

        assertThrows(BusinessException.class, () -> service.updateCurrentInstitution(1L,
            new CurrentInstitutionUpdateRequest(" ", null, null, null, null, null, null)));
    }

    @Test
    void getCurrentInstitutionRejectsMissingInstitution() {
        when(institutionMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getCurrentInstitution(99L));
    }

    @AfterEach
    void clearAuthContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listInstitutionsReturnsAllForPlatformAdmin() {
        login(1L, Set.of("PLATFORM_ADMIN"));
        when(institutionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.listInstitutions();

        assertTrue(captureWrapper().getParamNameValuePairs().isEmpty(), "平台管理员不应附加机构条件");
    }

    @Test
    void listInstitutionsScopesToLoginInstitution() {
        login(7L, Set.of("INSTITUTION_ADMIN"));
        when(institutionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.listInstitutions();

        assertTrue(captureWrapper().getParamNameValuePairs().containsValue(7L),
            "普通账号应只查本机构");
    }

    @Test
    void listInstitutionsWithoutLoginDoesNotFilter() {
        when(institutionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.listInstitutions();

        assertTrue(captureWrapper().getParamNameValuePairs().isEmpty(), "无登录态不附加机构条件");
    }

    @Test
    void listInstitutionsReturnsEmptyWhenAccountHasNoInstitution() {
        login(null, Set.of("INSTITUTION_ADMIN"));

        assertTrue(service.listInstitutions().isEmpty());
        verify(institutionMapper, never()).selectList(any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LambdaQueryWrapper<InstitutionEntity> captureWrapper() {
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(institutionMapper).selectList(captor.capture());
        LambdaQueryWrapper<InstitutionEntity> wrapper = captor.getValue();
        // 参数是懒生成的：getSqlSegment() 之后才会落进 paramNameValuePairs
        wrapper.getSqlSegment();
        return wrapper;
    }

    private void login(Long orgId, Set<String> roles) {
        AuthenticatedUser user = new AuthenticatedUser(
            1L, orgId, "admin", roles, Set.of(), false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }
}
