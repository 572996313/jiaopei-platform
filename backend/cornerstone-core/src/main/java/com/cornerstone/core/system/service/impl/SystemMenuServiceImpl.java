package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.domain.SystemMenuEntity;
import com.cornerstone.core.system.domain.SystemRoleMenuEntity;
import com.cornerstone.core.system.dto.MenuCreateRequest;
import com.cornerstone.core.system.dto.MenuUpdateRequest;
import com.cornerstone.core.system.dto.MenuView;
import com.cornerstone.core.system.dto.RoleAuthorizationView;
import com.cornerstone.core.system.dto.RoleMenuUpdateRequest;
import com.cornerstone.core.system.mapper.SystemMenuMapper;
import com.cornerstone.core.system.mapper.SystemRoleMenuMapper;
import com.cornerstone.core.system.service.ActionCatalogService;
import com.cornerstone.core.system.service.RoleDataScopeService;
import com.cornerstone.core.system.service.SystemMenuService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemMenuServiceImpl implements SystemMenuService {

    private static final String ACTIVE = "active";

    private static final String PLATFORM_ADMIN_MENU_PERMISSION = "system:menu:manage";
    private static final String PLATFORM_ROOT_MENU_CODE = "system";
    private static final String PORTAL_ADMIN = "admin";
    private static final String TYPE_MENU = "menu";

    private final SystemMenuMapper menuMapper;
    private final SystemRoleMenuMapper roleMenuMapper;
    private final RoleMapper roleMapper;
    private final ActionCatalogService actionCatalogService;
    private final RoleDataScopeService roleDataScopeService;
    /**
     * 会话失效入口。用 ObjectProvider 延迟解析而不是直接构造注入：
     * AuthApplicationServiceImpl → UserAccessTokenService → SystemMenuService 已经成环，
     * 这里再直接注回 AuthApplicationService 会让容器启动报 circular reference。
     */
    private final ObjectProvider<AuthApplicationService> authApplicationService;

    public SystemMenuServiceImpl(SystemMenuMapper menuMapper, SystemRoleMenuMapper roleMenuMapper,
                                 RoleMapper roleMapper, ActionCatalogService actionCatalogService,
                                 RoleDataScopeService roleDataScopeService,
                                 ObjectProvider<AuthApplicationService> authApplicationService) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleMapper = roleMapper;
        this.actionCatalogService = actionCatalogService;
        this.roleDataScopeService = roleDataScopeService;
        this.authApplicationService = authApplicationService;
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
        String previousPermissionCode = entity.getPermissionCode();
        String previousStatus = entity.getStatus();
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
        // 改权限码或停用节点会改变持有者的权限快照；底座此前没覆盖这条路径，
        // 不踢会话会让旧权限在令牌过期前继续可用（AGENTS「安全与数据一致性约定」第 2 条）。
        if (!java.util.Objects.equals(previousPermissionCode, entity.getPermissionCode())
            || !java.util.Objects.equals(previousStatus, entity.getStatus())) {
            invalidateRolesHoldingMenu(id);
        }
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
        invalidateRolesHoldingMenu(id);
        menuMapper.physicalDeleteById(id);
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
        // 菜单授权决定权限快照，改完必须踢掉该角色的在线会话，否则收回的权限在令牌过期前仍然可用
        authApplicationService.getObject().invalidateUserSessionsByRole(roleId);
        return new ArrayList<>(menuIds);
    }

    @Override
    public RoleAuthorizationView getRoleAuthorization(Long roleId, Long institutionId) {
        RoleEntity role = requireRole(roleId);
        return new RoleAuthorizationView(roleId, getRoleMenuIds(roleId),
            splitPermissionCodes(role.getPermissionCodes()),
            roleDataScopeService.list(roleId, institutionId));
    }

    @Override
    @Transactional
    public RoleAuthorizationView updateRoleAuthorization(Long roleId, RoleMenuUpdateRequest request,
                                                        Long institutionId) {
        RoleEntity role = requireRole(roleId);
        Set<Long> requested = new LinkedHashSet<>(request.menuIds() == null ? List.of() : request.menuIds());
        Set<Long> managed = actionCatalogService.managedMenuIds();

        Set<Long> outsideCatalog = new LinkedHashSet<>(requested);
        outsideCatalog.removeAll(managed);
        if (!outsideCatalog.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "授权项不在可授权目录内，请刷新页面后重试");
        }

        Map<Long, SystemMenuEntity> menuById = menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>())
            .stream()
            .collect(Collectors.toMap(SystemMenuEntity::getId, Function.identity(), (left, right) -> left));
        Set<Long> pageIds = menuById.values().stream()
            .filter(menu -> PORTAL_ADMIN.equals(menu.getPortal()) && TYPE_MENU.equals(menu.getMenuType()))
            .map(SystemMenuEntity::getId)
            .collect(Collectors.toSet());

        // 只有页面节点需要补祖先目录；按钮节点的 parent 不一定就是它的业务页面
        // （如「档案维护」挂在教师页下，却同时给学员页的写操作授权）。
        Set<Long> touchedPageIds = new LinkedHashSet<>();
        Map<Long, Long> pageIdByGrant = actionCatalogService.pageMenuIdByGrantMenuId();
        for (Long menuId : requested) {
            if (pageIds.contains(menuId)) {
                touchedPageIds.add(menuId);
            }
            Long pageId = pageIdByGrant.get(menuId);
            if (pageId != null) {
                touchedPageIds.add(pageId);
            }
        }

        // 目录之外的既有权（如小程序端节点）界面看不到，保存时原样保留，避免静默收权。
        Set<Long> existingMenuIds = new LinkedHashSet<>(getRoleMenuIds(roleId));
        Set<Long> finalMenuIds = new LinkedHashSet<>();
        for (Long menuId : existingMenuIds) {
            if (!managed.contains(menuId)) {
                finalMenuIds.add(menuId);
            }
        }

        // 只拦"新增"系统设置子树的页面：历史授权原样保存或撤销都不该被 403 挡住，
        // 否则非平台管理员连改一下角色名称都会失败。
        Set<Long> addedPageIds = new LinkedHashSet<>(touchedPageIds);
        addedPageIds.removeAll(existingMenuIds);
        requirePlatformSubtreePermission(addedPageIds, menuById);

        Set<Long> desired = new LinkedHashSet<>(requested);
        for (Long pageId : touchedPageIds) {
            addAncestors(pageId, menuById, desired);
        }
        finalMenuIds.addAll(desired);

        roleMenuMapper.physicalDeleteByRoleId(roleId);
        for (Long menuId : finalMenuIds) {
            SystemRoleMenuEntity relation = new SystemRoleMenuEntity();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            relation.setDeleted(Boolean.FALSE);
            roleMenuMapper.insert(relation);
        }
        // 数据范围与页面/按钮授权同事务保存；null 表示旧前端未提交这一块，保持原范围不动
        roleDataScopeService.replace(roleId, institutionId, request.dataScopes());
        authApplicationService.getObject().invalidateUserSessionsByRole(roleId);
        return new RoleAuthorizationView(roleId, new ArrayList<>(finalMenuIds),
            splitPermissionCodes(role.getPermissionCodes()),
            roleDataScopeService.list(roleId, institutionId));
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
        // 端隔离:只返回当前用户所属端(portal)的功能清单。旧令牌无 portal 时兜底按 admin 过滤,保证后台行为不变
        String portal = user.portal() == null ? "admin" : user.portal();
        List<SystemMenuEntity> menus = menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
                .in(SystemMenuEntity::getId, menuIds)
                .eq(SystemMenuEntity::getStatus, ACTIVE)
                .eq(SystemMenuEntity::getVisible, Boolean.TRUE)
                .eq(SystemMenuEntity::getPortal, portal))
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

    /**
     * 补祖先目录：`buildTree` 会把父节点未授权的子节点提升成根，
     * 只勾页面不勾目录的表现是"分组消失、入口散在顶层"。
     */
    private void addAncestors(Long menuId, Map<Long, SystemMenuEntity> menuById, Set<Long> target) {
        SystemMenuEntity current = menuById.get(menuId);
        Set<Long> visited = new LinkedHashSet<>();
        while (current != null && current.getParentId() != null && visited.add(current.getId())) {
            Long parentId = current.getParentId();
            target.add(parentId);
            current = menuById.get(parentId);
        }
    }

    /**
     * 非平台管理员不能给角色授予「系统设置」子树：底座用户/角色/机构三条链路尚未按机构范围校验，
     * 勾上即跨机构读写（AGENTS「安全与数据一致性约定」第 8 条）。
     */
    private void requirePlatformSubtreePermission(Set<Long> pageIds, Map<Long, SystemMenuEntity> menuById) {
        if (pageIds.isEmpty() || AuthContext.hasPermission(PLATFORM_ADMIN_MENU_PERMISSION)) {
            return;
        }
        for (Long pageId : pageIds) {
            SystemMenuEntity current = menuById.get(pageId);
            Set<Long> visited = new LinkedHashSet<>();
            while (current != null && visited.add(current.getId())) {
                if (PLATFORM_ROOT_MENU_CODE.equals(current.getMenuCode())) {
                    throw new BusinessException(ResultCode.FORBIDDEN,
                        "「系统设置」下的页面只能由平台管理员授权");
                }
                current = current.getParentId() == null ? null : menuById.get(current.getParentId());
            }
        }
    }

    private void invalidateRolesHoldingMenu(Long menuId) {
        List<Long> roleIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SystemRoleMenuEntity>()
                .eq(SystemRoleMenuEntity::getMenuId, menuId))
            .stream()
            .map(SystemRoleMenuEntity::getRoleId)
            .distinct()
            .toList();
        for (Long roleId : roleIds) {
            authApplicationService.getObject().invalidateUserSessionsByRole(roleId);
        }
    }

    private List<String> splitPermissionCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
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
