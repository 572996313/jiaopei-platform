package com.jiaopei.platform.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.auth.context.AuthContext;
import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.domain.RoleEntity;
import com.jiaopei.platform.auth.mapper.RoleMapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.system.domain.SystemMenuEntity;
import com.jiaopei.platform.system.domain.SystemRoleMenuEntity;
import com.jiaopei.platform.system.dto.MenuCreateRequest;
import com.jiaopei.platform.system.dto.MenuUpdateRequest;
import com.jiaopei.platform.system.dto.MenuView;
import com.jiaopei.platform.system.dto.RoleMenuUpdateRequest;
import com.jiaopei.platform.system.mapper.SystemMenuMapper;
import com.jiaopei.platform.system.mapper.SystemRoleMenuMapper;
import com.jiaopei.platform.system.service.SystemMenuService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemMenuServiceImpl implements SystemMenuService {

    private static final String ACTIVE = "active";

    private final SystemMenuMapper menuMapper;
    private final SystemRoleMenuMapper roleMenuMapper;
    private final RoleMapper roleMapper;

    public SystemMenuServiceImpl(SystemMenuMapper menuMapper, SystemRoleMenuMapper roleMenuMapper,
                                 RoleMapper roleMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<MenuView> tree() {
        return buildTree(listMenuEntities());
    }

    @Override
    public List<MenuView> listMenus() {
        return listMenuEntities().stream()
            .map(menu -> toView(menu, List.of()))
            .toList();
    }

    @Override
    @Transactional
    public MenuView createMenu(MenuCreateRequest request) {
        validateParent(request.parentId(), null);
        SystemMenuEntity entity = new SystemMenuEntity();
        entity.setParentId(request.parentId());
        entity.setMenuCode(normalize(request.menuCode()));
        entity.setMenuName(normalize(request.menuName()));
        entity.setMenuType(normalize(request.menuType()));
        entity.setPath(normalize(request.path()));
        entity.setComponent(normalize(request.component()));
        entity.setPermissionCode(normalize(request.permissionCode()));
        entity.setIcon(normalize(request.icon()));
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setVisible(request.visible() == null ? Boolean.TRUE : request.visible());
        entity.setStatus(defaultStatus(request.status()));
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            menuMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "菜单编码已存在：" + entity.getMenuCode());
        }
        return toView(entity, List.of());
    }

    @Override
    @Transactional
    public MenuView updateMenu(Long id, MenuUpdateRequest request) {
        SystemMenuEntity entity = requireMenu(id);
        validateParent(request.parentId(), id);
        entity.setParentId(request.parentId());
        entity.setMenuName(normalize(request.menuName()));
        entity.setMenuType(normalize(request.menuType()));
        entity.setPath(normalize(request.path()));
        entity.setComponent(normalize(request.component()));
        entity.setPermissionCode(normalize(request.permissionCode()));
        entity.setIcon(normalize(request.icon()));
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setVisible(request.visible() == null ? Boolean.TRUE : request.visible());
        entity.setStatus(normalize(request.status()));
        entity.setRemark(request.remark());
        menuMapper.updateById(entity);
        return toView(entity, List.of());
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        requireMenu(id);
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SystemMenuEntity>()
            .eq(SystemMenuEntity::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "菜单存在子节点，不能删除");
        }
        menuMapper.deleteById(id);
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        requireRole(roleId);
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SystemRoleMenuEntity>()
                .eq(SystemRoleMenuEntity::getRoleId, roleId))
            .stream()
            .map(SystemRoleMenuEntity::getMenuId)
            .toList();
    }

    @Override
    public Set<String> getPermissionCodesByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SystemRoleMenuEntity>()
                .in(SystemRoleMenuEntity::getRoleId, roleIds))
            .stream()
            .map(SystemRoleMenuEntity::getMenuId)
            .distinct()
            .toList();
        if (menuIds.isEmpty()) {
            return Set.of();
        }
        return menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
                .in(SystemMenuEntity::getId, menuIds)
                .eq(SystemMenuEntity::getStatus, ACTIVE)
                .isNotNull(SystemMenuEntity::getPermissionCode))
            .stream()
            .map(SystemMenuEntity::getPermissionCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    @Transactional
    public List<Long> updateRoleMenus(Long roleId, RoleMenuUpdateRequest request) {
        requireRole(roleId);
        Set<Long> menuIds = new LinkedHashSet<>(request.menuIds() == null ? List.of() : request.menuIds());
        validateMenus(menuIds);
        roleMenuMapper.physicalDeleteByRoleId(roleId);
        for (Long menuId : menuIds) {
            SystemRoleMenuEntity relation = new SystemRoleMenuEntity();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            relation.setDeleted(Boolean.FALSE);
            roleMenuMapper.insert(relation);
        }
        return new ArrayList<>(menuIds);
    }

    @Override
    public List<MenuView> currentUserMenus() {
        AuthenticatedUser user = AuthContext.requireUser();
        if (user.roles().isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .in(RoleEntity::getRoleCode, user.roles())
                .eq(RoleEntity::getStatus, ACTIVE))
            .stream()
            .map(RoleEntity::getId)
            .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SystemRoleMenuEntity>()
                .in(SystemRoleMenuEntity::getRoleId, roleIds))
            .stream()
            .map(SystemRoleMenuEntity::getMenuId)
            .distinct()
            .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<SystemMenuEntity> menus = menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
                .in(SystemMenuEntity::getId, menuIds)
                .eq(SystemMenuEntity::getStatus, ACTIVE)
                .eq(SystemMenuEntity::getVisible, Boolean.TRUE))
            .stream()
            .filter(menu -> menu.getPermissionCode() == null
                || menu.getPermissionCode().isBlank()
                || user.permissions().contains(menu.getPermissionCode()))
            .sorted(menuComparator())
            .toList();
        return buildTree(menus);
    }

    private List<SystemMenuEntity> listMenuEntities() {
        return menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>())
            .stream()
            .sorted(menuComparator())
            .toList();
    }

    private List<MenuView> buildTree(List<SystemMenuEntity> menus) {
        Map<Long, SystemMenuEntity> menuById = menus.stream()
            .collect(Collectors.toMap(SystemMenuEntity::getId, Function.identity(), (left, right) -> left));
        Map<Long, List<SystemMenuEntity>> childrenByParent = menus.stream()
            .filter(menu -> menu.getParentId() != null && menuById.containsKey(menu.getParentId()))
            .collect(Collectors.groupingBy(SystemMenuEntity::getParentId));
        return menus.stream()
            .filter(menu -> menu.getParentId() == null || !menuById.containsKey(menu.getParentId()))
            .map(menu -> toTreeView(menu, childrenByParent))
            .toList();
    }

    private MenuView toTreeView(SystemMenuEntity menu, Map<Long, List<SystemMenuEntity>> childrenByParent) {
        List<MenuView> children = childrenByParent.getOrDefault(menu.getId(), List.of()).stream()
            .sorted(menuComparator())
            .map(child -> toTreeView(child, childrenByParent))
            .toList();
        return toView(menu, children);
    }

    private MenuView toView(SystemMenuEntity entity, List<MenuView> children) {
        return new MenuView(
            entity.getId(),
            entity.getParentId(),
            entity.getMenuCode(),
            entity.getMenuName(),
            entity.getMenuType(),
            entity.getPath(),
            entity.getComponent(),
            entity.getPermissionCode(),
            entity.getIcon(),
            entity.getSortNo(),
            entity.getVisible(),
            entity.getStatus(),
            entity.getRemark(),
            children
        );
    }

    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上级菜单不能选择自己");
        }
        requireMenu(parentId);
    }

    private void validateMenus(Set<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        List<SystemMenuEntity> menus = menuMapper.selectBatchIds(menuIds);
        if (menus.size() != menuIds.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "部分菜单不存在");
        }
    }

    private RoleEntity requireRole(Long roleId) {
        RoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在：" + roleId);
        }
        return role;
    }

    private SystemMenuEntity requireMenu(Long id) {
        SystemMenuEntity entity = menuMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "菜单不存在：" + id);
        }
        return entity;
    }

    private Comparator<SystemMenuEntity> menuComparator() {
        return Comparator
            .comparing((SystemMenuEntity menu) -> menu.getSortNo() == null ? 0 : menu.getSortNo())
            .thenComparing(SystemMenuEntity::getMenuCode);
    }

    private String defaultStatus(String status) {
        String normalized = normalize(status);
        return normalized == null || normalized.isBlank() ? ACTIVE : normalized;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
