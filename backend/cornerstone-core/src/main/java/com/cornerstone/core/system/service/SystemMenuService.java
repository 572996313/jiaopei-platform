package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.MenuCreateRequest;
import com.cornerstone.core.system.dto.MenuUpdateRequest;
import com.cornerstone.core.system.dto.MenuView;
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

    Set<String> getPermissionCodesByRoleIds(List<Long> roleIds);

    List<MenuView> currentUserMenus();
}
