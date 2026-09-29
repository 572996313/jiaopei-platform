package com.cornerstone.core.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.domain.SystemMenuEntity;
import com.cornerstone.core.system.domain.SystemRoleMenuEntity;
import com.cornerstone.core.system.dto.NavigationCatalogCreateRequest;
import com.cornerstone.core.system.dto.NavigationCatalogCreateResultView;
import com.cornerstone.core.system.dto.NavigationNodeUpdateRequest;
import com.cornerstone.core.system.dto.NavigationUpdateRequest;
import com.cornerstone.core.system.dto.NavigationView;
import com.cornerstone.core.system.mapper.SystemMenuMapper;
import com.cornerstone.core.system.mapper.SystemRoleMenuMapper;
import com.cornerstone.core.system.service.impl.SystemNavigationServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导航编排的校验口径：只拦"改完找不到这一页 / 整组入口消失 / 两个同名入口"。
 * 这一页开放给现场操作员，漏拦的那几条就是客户自己把侧边栏改坏、而我们收不到任何报错的路径。
 */
class SystemNavigationServiceImplTest {

    private final SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
    private final SystemRoleMenuMapper roleMenuMapper = mock(SystemRoleMenuMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final SystemNavigationServiceImpl service =
        new SystemNavigationServiceImpl(menuMapper, roleMenuMapper, roleMapper);

    @Test
    void saveWritesOnlyTheFiveEditableColumns() {
        SystemMenuEntity page = node(16L, 15L, "排课管理", "menu", 10);
        page.setPath("/schedule");
        page.setPermissionCode("education:read");
        stub(List.of(catalog(15L, "排课与上课", 20), page));

        service.saveNavigation(new NavigationUpdateRequest(
            List.of(new NavigationNodeUpdateRequest(16L, " 排课管理 ", "Clock", 30, null, false))));

        ArgumentCaptor<SystemMenuEntity> captor = ArgumentCaptor.forClass(SystemMenuEntity.class);
        verify(menuMapper).updateById(captor.capture());
        SystemMenuEntity saved = captor.getValue();
        assertEquals("排课管理", saved.getMenuName());
        assertEquals("Clock", saved.getIcon());
        assertEquals(30, saved.getSortNo().intValue());
        // 父级传 null 是"提升为顶层分组"，靠实体上的 FieldStrategy.ALWAYS 才落得进去
        assertNull(saved.getParentId());
        assertFalse(saved.getVisible());
        // 路由与权限码不在可写列里：改掉就会把人挡在 403 或空白页
        assertEquals("/schedule", saved.getPath());
        assertEquals("education:read", saved.getPermissionCode());
        assertEquals("active", saved.getStatus());
    }

    @Test
    void batchIsRejectedBeforeAnythingIsWritten() {
        SystemMenuEntity root = catalog(1L, "系统设置", 100);
        root.setMenuCode("system");
        stub(List.of(catalog(15L, "排课与上课", 20), node(16L, 15L, "排课管理", "menu", 10), root));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.saveNavigation(
            new NavigationUpdateRequest(List.of(
                new NavigationNodeUpdateRequest(16L, "排课管理", "", 10, 15L, true),
                new NavigationNodeUpdateRequest(1L, "系统设置", "", 100, null, true)))));

        assertTrue(exception.getMessage().contains("系统设置"));
        verify(menuMapper, never()).updateById(any(SystemMenuEntity.class));
    }

    @Test
    void sameNodeSubmittedTwiceIsRejected() {
        stub(List.of(catalog(15L, "排课与上课", 20), node(16L, 15L, "排课管理", "menu", 10)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.saveNavigation(
            new NavigationUpdateRequest(List.of(
                new NavigationNodeUpdateRequest(16L, "A", "", 10, 15L, true),
                new NavigationNodeUpdateRequest(16L, "B", "", 10, 15L, true)))));

        assertTrue(exception.getMessage().contains("提交了两次"));
    }

    @Test
    void sidebarStaysTwoLevelsDeep() {
        SystemMenuEntity nested = catalog(200L, "校区", 10);
        nested.setParentId(150L);
        stub(List.of(catalog(150L, "基础资料", 40), nested, node(16L, 200L, "节次明细", "menu", 10)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.saveNavigation(
            new NavigationUpdateRequest(List.of(new NavigationNodeUpdateRequest(200L, "校区", "", 10, 150L, true)))));

        assertTrue(exception.getMessage().contains("两层"));
    }

    @Test
    void pagesCannotMoveIntoLockedSubtree() {
        SystemMenuEntity root = catalog(1L, "系统设置", 100);
        root.setMenuCode("system");
        SystemMenuEntity lowcode = catalog(13L, "低代码配置", 120);
        lowcode.setParentId(1L);
        stub(List.of(root, lowcode, catalog(15L, "排课与上课", 20), node(21L, 15L, "时段模板", "menu", 60)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.saveNavigation(
            new NavigationUpdateRequest(List.of(new NavigationNodeUpdateRequest(21L, "时段模板", "", 1, 13L, true)))));

        assertTrue(exception.getMessage().contains("系统设置"));
    }

    @Test
    void topLevelEntriesMustNotShareAName() {
        stub(List.of(catalog(15L, "排课与上课", 20), node(25L, null, "公告中心", "menu", 50)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.saveNavigation(
            new NavigationUpdateRequest(List.of(
                new NavigationNodeUpdateRequest(25L, " 排课与上课 ", "Bell", 50, null, true)))));

        assertTrue(exception.getMessage().contains("顶层名称重复"));
    }

    @Test
    void creatingACatalogGrantsEveryActiveRoleInTheSameTransaction() {
        stub(List.of(catalog(15L, "排课与上课", 20)));
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(role(1L), role(110L)));

        NavigationCatalogCreateResultView result =
            service.createCatalog(new NavigationCatalogCreateRequest("新分组", "Grid", null));

        assertEquals(2L, result.grantedRoleIdCount());
        verify(roleMenuMapper, times(2)).insert(any(SystemRoleMenuEntity.class));
        ArgumentCaptor<SystemMenuEntity> captor = ArgumentCaptor.forClass(SystemMenuEntity.class);
        verify(menuMapper).insert(captor.capture());
        SystemMenuEntity created = captor.getValue();
        assertEquals("catalog", created.getMenuType());
        assertNull(created.getPermissionCode());
        assertNull(created.getPath());
        assertTrue(created.getVisible());
        // 排在业务段末尾：现有未锁定顶层节点最大顺序号 20
        assertEquals(30, created.getSortNo().intValue());
    }

    @Test
    void creatingACatalogWithAnExistingTopLevelNameIsRejected() {
        stub(List.of(catalog(15L, "排课与上课", 20)));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.createCatalog(new NavigationCatalogCreateRequest("排课与上课", null, null)));

        assertTrue(exception.getMessage().contains("顶层名称重复"));
        verify(menuMapper, never()).insert(any(SystemMenuEntity.class));
    }

    @Test
    void listFlagsLockedNodesAndGrantCounts() {
        SystemMenuEntity root = catalog(1L, "系统设置", 100);
        root.setMenuCode("system");
        stub(List.of(root, node(2L, 1L, "用户管理", "menu", 20), catalog(15L, "排课与上课", 20)));
        SystemRoleMenuEntity relation = new SystemRoleMenuEntity();
        relation.setRoleId(1L);
        relation.setMenuId(15L);
        when(roleMenuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(relation));
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(role(1L)));

        List<NavigationView> views = service.listNavigation();

        assertEquals(3, views.size());
        NavigationView business = viewOf(views, 15L);
        NavigationView lockedUser = viewOf(views, 2L);
        assertFalse(business.locked());
        assertEquals(1L, business.grantedRoleCount().longValue());
        assertTrue(lockedUser.locked());
        assertEquals(0L, lockedUser.grantedRoleCount().longValue());
    }

    private static NavigationView viewOf(List<NavigationView> views, long id) {
        return views.stream().filter(view -> view.id() == id).findFirst().orElseThrow();
    }

    private void stub(List<SystemMenuEntity> nodes) {
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(nodes);
    }

    private static SystemMenuEntity catalog(Long id, String name, int sortNo) {
        SystemMenuEntity entity = node(id, null, name, "catalog", sortNo);
        entity.setIcon("Calendar");
        return entity;
    }

    private static SystemMenuEntity node(Long id, Long parentId, String name, String type, int sortNo) {
        SystemMenuEntity entity = new SystemMenuEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setPortal("admin");
        entity.setMenuCode("code_" + id);
        entity.setMenuName(name);
        entity.setMenuType(type);
        entity.setSortNo(sortNo);
        entity.setVisible(Boolean.TRUE);
        entity.setStatus("active");
        entity.setDeleted(Boolean.FALSE);
        return entity;
    }

    private static RoleEntity role(Long id) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setStatus("active");
        return role;
    }
}
