package com.cornerstone.core.system;

import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.system.domain.SystemMenuEntity;
import com.cornerstone.core.system.dto.ActionCatalogPageView;
import com.cornerstone.core.system.mapper.SystemMenuMapper;
import com.cornerstone.core.system.mapper.SystemRoleMenuMapper;
import com.cornerstone.core.system.service.impl.ActionCatalogServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 授权弹窗左侧页面列表的排序口径：与侧边栏一致——先按顶层分组的 sort_no，
 * 再按组内页面 sort_no；顶层散页自成一组。
 */
class ActionCatalogServiceImplTest {

    @Test
    void pagesAreGroupedAndSortedLikeSidebar() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        when(menuMapper.selectList(any())).thenReturn(List.of(
            catalog(15L, "education_scheduling", "排课与上课", 20),
            page(16L, 15L, "education_scheduling", "排课管理", 10),
            page(17L, 15L, "education_execution", "课次执行", 20),
            catalog(130L, "organization_resources", "基础资料", 40),
            page(134L, 130L, "education_students", "学员档案", 10),
            page(135L, 130L, "education_courses", "课程档案", 20),
            page(25L, null, "announcement_center", "公告中心", 50),
            catalog(1L, "system", "系统设置", 100),
            page(3L, 1L, "system_roles", "角色管理", 120)
        ));

        ActionCatalogServiceImpl service = new ActionCatalogServiceImpl(
            menuMapper, mock(SystemRoleMenuMapper.class), mock(RoleMapper.class), List.of());
        List<ActionCatalogPageView> pages = service.catalog(null).pages();

        assertEquals(
            List.of(16L, 17L, 134L, 135L, 25L, 3L),
            pages.stream().map(ActionCatalogPageView::menuId).toList());
        assertEquals(List.of(15L, 15L, 130L, 130L, 25L, 1L),
            pages.stream().map(ActionCatalogPageView::groupId).toList());
        assertEquals(List.of("排课与上课", "排课与上课", "基础资料", "基础资料", "公告中心", "系统设置"),
            pages.stream().map(ActionCatalogPageView::groupName).toList());
    }

    @Test
    void missingParentFallsBackToPageItself() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        when(menuMapper.selectList(any())).thenReturn(List.of(
            page(16L, 99L, "education_scheduling", "排课管理", 10)
        ));

        ActionCatalogServiceImpl service = new ActionCatalogServiceImpl(
            menuMapper, mock(SystemRoleMenuMapper.class), mock(RoleMapper.class), List.of());
        ActionCatalogPageView page = service.catalog(null).pages().get(0);

        assertEquals(16L, page.groupId());
        assertEquals("排课管理", page.groupName());
    }

    private static SystemMenuEntity catalog(Long id, String code, String name, int sortNo) {
        return node(id, null, code, name, "catalog", sortNo);
    }

    private static SystemMenuEntity page(Long id, Long parentId, String code, String name, int sortNo) {
        return node(id, parentId, code, name, "menu", sortNo);
    }

    private static SystemMenuEntity node(Long id, Long parentId, String code, String name,
                                         String type, int sortNo) {
        SystemMenuEntity menu = new SystemMenuEntity();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuCode(code);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setPortal("admin");
        menu.setSortNo(sortNo);
        menu.setStatus("active");
        return menu;
    }
}
