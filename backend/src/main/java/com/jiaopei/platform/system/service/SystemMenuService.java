package com.jiaopei.platform.system.service;

import com.jiaopei.platform.system.dto.MenuCreateRequest;
import com.jiaopei.platform.system.dto.MenuUpdateRequest;
import com.jiaopei.platform.system.dto.MenuView;
import com.jiaopei.platform.system.dto.RoleMenuUpdateRequest;
import java.util.List;

public interface SystemMenuService {

    List<MenuView> tree();

    List<MenuView> listMenus();

    MenuView createMenu(MenuCreateRequest request);

    MenuView updateMenu(Long id, MenuUpdateRequest request);

    void deleteMenu(Long id);

    List<Long> getRoleMenuIds(Long roleId);

    List<Long> updateRoleMenus(Long roleId, RoleMenuUpdateRequest request);

    List<MenuView> currentUserMenus();
}
