package com.cornerstone.core.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.system.domain.SystemMenuEntity;
import com.cornerstone.core.system.domain.SystemRoleMenuEntity;
import com.cornerstone.core.system.dto.RoleMenuUpdateRequest;
import com.cornerstone.core.system.mapper.SystemMenuMapper;
import com.cornerstone.core.system.mapper.SystemRoleMenuMapper;
import com.cornerstone.core.system.service.impl.SystemMenuServiceImpl;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
        SystemMenuServiceImpl service = new SystemMenuServiceImpl(menuMapper, roleMenuMapper, roleMapper);

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
    }

    @Test
    void treeBuildsParentChildMenus() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemMenuServiceImpl service = new SystemMenuServiceImpl(
            menuMapper,
            mock(SystemRoleMenuMapper.class),
            mock(RoleMapper.class)
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
        SystemMenuServiceImpl service = new SystemMenuServiceImpl(menuMapper, roleMenuMapper, roleMapper);

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
