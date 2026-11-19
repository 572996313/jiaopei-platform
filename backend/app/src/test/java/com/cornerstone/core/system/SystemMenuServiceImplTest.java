package com.cornerstone.core.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.system.domain.SystemMenuEntity;
import com.cornerstone.core.system.domain.SystemRoleMenuEntity;
import com.cornerstone.core.system.dto.RoleAuthorizationView;
import com.cornerstone.core.system.dto.RoleMenuUpdateRequest;
import com.cornerstone.core.system.mapper.SystemMenuMapper;
import com.cornerstone.core.system.mapper.SystemRoleMenuMapper;
import com.cornerstone.core.system.service.ActionCatalogService;
import com.cornerstone.core.system.service.impl.SystemMenuServiceImpl;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

class SystemMenuServiceImplTest {

    @Test
    void updateRoleMenusUsesPhysicalDeleteBeforeInsert() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemRoleMenuMapper roleMenuMapper = mock(SystemRoleMenuMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        AuthApplicationService authApplicationService = mock(AuthApplicationService.class);
        SystemMenuServiceImpl service = new SystemMenuServiceImpl(
            menuMapper, roleMenuMapper, roleMapper, mock(ActionCatalogService.class),
            authApplicationServiceProvider(authApplicationService));

        RoleEntity role = new RoleEntity();
        role.setId(1L);
        when(roleMapper.selectById(1L)).thenReturn(role);

        SystemMenuEntity menu = new SystemMenuEntity();
        menu.setId(2L);
        when(menuMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(menu));

        List<Long> result = service.updateRoleMenus(1L, new RoleMenuUpdateRequest(List.of(2L)));

        assertEquals(List.of(2L), result);
        verify(roleMenuMapper).physicalDeleteByRoleId(1L);
        verify(roleMenuMapper, times(1)).insert(any(SystemRoleMenuEntity.class));
        // 回归:改角色菜单授权后必须失效该角色持有用户的会话，否则收回的权限在令牌过期前继续可用
        verify(authApplicationService).invalidateUserSessionsByRole(1L);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuthApplicationService> authApplicationServiceProvider(
        AuthApplicationService service) {
        ObjectProvider<AuthApplicationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(service);
        return provider;
    }

    @Test
    void updateRoleAuthorizationKeepsGrantsOutsideCatalog() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemRoleMenuMapper roleMenuMapper = mock(SystemRoleMenuMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        AuthApplicationService authApplicationService = mock(AuthApplicationService.class);
        ActionCatalogService actionCatalogService = mock(ActionCatalogService.class);
        SystemMenuServiceImpl service = new SystemMenuServiceImpl(
            menuMapper, roleMenuMapper, roleMapper, actionCatalogService,
            authApplicationServiceProvider(authApplicationService));

        RoleEntity role = new RoleEntity();
        role.setId(1L);
        when(roleMapper.selectById(1L)).thenReturn(role);
        // 目录只托管业务页(2)与它的写节点(3);小程序节点 210 不在目录内
        when(actionCatalogService.managedMenuIds()).thenReturn(Set.of(2L, 3L));
        when(actionCatalogService.pageMenuIdByGrantMenuId()).thenReturn(Map.of(3L, 2L));

        SystemMenuEntity page = menu(2L, null, "business_page", 1);
        page.setPortal("admin");
        SystemMenuEntity writeButton = menu(3L, 2L, "business_write", 2);
        writeButton.setPortal("admin");
        writeButton.setMenuType("button");
        SystemMenuEntity miniapp = menu(210L, null, "parent_home", 3);
        miniapp.setPortal("parent");
        when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(page, writeButton, miniapp));
        when(roleMenuMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(roleMenu(1L, 210L), roleMenu(1L, 3L)));

        RoleAuthorizationView result = service.updateRoleAuthorization(
            1L, new RoleMenuUpdateRequest(List.of(2L)));

        // 界面看不到的目录外授权(210)必须原样保留;目录内未勾的写节点(3)被撤销
        assertEquals(Set.of(2L, 210L), Set.copyOf(result.menuIds()));
        ArgumentCaptor<SystemRoleMenuEntity> captor = ArgumentCaptor.forClass(SystemRoleMenuEntity.class);
        verify(roleMenuMapper, times(2)).insert(captor.capture());
        assertEquals(Set.of(2L, 210L), captor.getAllValues().stream()
            .map(SystemRoleMenuEntity::getMenuId)
            .collect(Collectors.toSet()));
        verify(authApplicationService).invalidateUserSessionsByRole(1L);
    }

    @Test
    void treeBuildsParentChildMenus() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemMenuServiceImpl service = new SystemMenuServiceImpl(
            menuMapper,
            mock(SystemRoleMenuMapper.class),
            mock(RoleMapper.class),
            mock(ActionCatalogService.class),
            authApplicationServiceProvider(mock(AuthApplicationService.class))
        );
        SystemMenuEntity root = menu(1L, null, "root", 1);
        SystemMenuEntity child = menu(2L, 1L, "child", 2);
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(root, child));

        assertEquals(1, service.tree().size());
        assertEquals(1, service.tree().getFirst().children().size());
    }

    @Test
    void currentUserMenusFiltersByPortal() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemRoleMenuMapper roleMenuMapper = mock(SystemRoleMenuMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        SystemMenuServiceImpl service = new SystemMenuServiceImpl(
            menuMapper, roleMenuMapper, roleMapper, mock(ActionCatalogService.class),
            authApplicationServiceProvider(mock(AuthApplicationService.class)));

        // 家长端用户:角色 MINIAPP_PARENT → role id 102 → 已授权菜单 id 210
        RoleEntity role = new RoleEntity();
        role.setId(102L);
        role.setRoleCode("MINIAPP_PARENT");
        role.setStatus("active");
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(role));

        SystemRoleMenuEntity rel = new SystemRoleMenuEntity();
        rel.setRoleId(102L);
        rel.setMenuId(210L);
        when(roleMenuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rel));

        // mock 模拟"SQL 按 portal 过滤后的结果":只返回家长端菜单(SQL 层已按条件裁剪)
        SystemMenuEntity parentMenu = menu(210L, null, "parent_home", 1);
        parentMenu.setPortal("parent");
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(parentMenu));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUser(1L, 1L, "wx_p", Set.of("MINIAPP_PARENT"), Set.of("miniapp:access"), false, "parent"),
            "token", Set.of()));

        var result = service.currentUserMenus();

        assertEquals(1, result.size());
        assertEquals("parent_home", result.getFirst().menuCode());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private SystemRoleMenuEntity roleMenu(Long roleId, Long menuId) {
        SystemRoleMenuEntity relation = new SystemRoleMenuEntity();
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        return relation;
    }

    private SystemMenuEntity menu(Long id, Long parentId, String code, int sortNo) {
        SystemMenuEntity menu = new SystemMenuEntity();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuCode(code);
        menu.setMenuName(code);
        menu.setMenuType("menu");
        menu.setSortNo(sortNo);
        menu.setVisible(Boolean.TRUE);
        menu.setStatus("active");
        return menu;
    }
}
