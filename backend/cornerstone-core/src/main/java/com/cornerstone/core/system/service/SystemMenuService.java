package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.MenuCreateRequest;
import com.cornerstone.core.system.dto.MenuUpdateRequest;
import com.cornerstone.core.system.dto.MenuView;
import com.cornerstone.core.system.dto.RoleAuthorizationView;
import com.cornerstone.core.system.dto.RoleMenuUpdateRequest;
import java.util.List;
import java.util.Set;

public interface SystemMenuService {

    List<MenuView> tree();

    List<MenuView> listMenus();

    MenuView createMenu(MenuCreateRequest request);

    MenuView updateMenu(Long id, MenuUpdateRequest request);

    void deleteMenu(Long id);

    List<Long> getRoleMenuIds(Long roleId);

    List<Long> updateRoleMenus(Long roleId, RoleMenuUpdateRequest request);

    /**
     * 角色授权界面读取：已勾选的页面/操作节点 + 历史预设权限码 + 当前机构的数据范围。
     */
    RoleAuthorizationView getRoleAuthorization(Long roleId, Long institutionId);

    /**
     * 角色授权界面保存：只接受按钮授权目录内的页面与操作节点，自动补齐目录祖先，
     * 并保留目录之外的既有权（如小程序端节点），避免界面看不到的授权被静默删除。
     * 数据范围与菜单授权在同一事务内保存，保存后统一失效该角色的在线会话。
     */
    RoleAuthorizationView updateRoleAuthorization(Long roleId, RoleMenuUpdateRequest request,
                                                 Long institutionId);

    Set<String> getPermissionCodesByRoleIds(List<Long> roleIds);

    List<MenuView> currentUserMenus();
}
