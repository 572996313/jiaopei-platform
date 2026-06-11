package com.jiaopei.platform.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.auth.domain.RoleEntity;
import com.jiaopei.platform.auth.mapper.RoleMapper;
import com.jiaopei.platform.system.domain.SystemMenuEntity;
import com.jiaopei.platform.system.domain.SystemRoleMenuEntity;
import com.jiaopei.platform.system.dto.RoleMenuUpdateRequest;
import com.jiaopei.platform.system.mapper.SystemMenuMapper;
import com.jiaopei.platform.system.mapper.SystemRoleMenuMapper;
import com.jiaopei.platform.system.service.impl.SystemMenuServiceImpl;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
