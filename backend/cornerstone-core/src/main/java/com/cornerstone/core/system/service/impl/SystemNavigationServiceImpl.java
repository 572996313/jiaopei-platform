package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.common.api.ResultCode;
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
import com.cornerstone.core.system.service.SystemNavigationService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导航编排的实现。
 *
 * 为什么单独一个服务而不复用 SystemMenuService：那一页能改路由与权限码，是平台管理员的工具；
 * 这一页开放给现场操作员，必须保证"改不出坏入口"。所以这里只写五列，并且把校验集中在
 * "会不会让入口消失 / 点不开"这一条判据上。
 */
@Service
public class SystemNavigationServiceImpl implements SystemNavigationService {

    private static final String ACTIVE = "active";
    private static final String ADMIN_PORTAL = "admin";
    private static final String TYPE_CATALOG = "catalog";
    private static final String TYPE_BUTTON = "button";
    /**
     * 底座段的根目录编码。「系统设置」下面的用户管理、角色管理、机构资料这三条链路
     * 后端还没比机构范围（AGENTS「安全与数据一致性约定」第 8 条），一旦允许客户把它们的页面节点
     * 挪进业务分组，等于绕过"不许给非平台角色勾系统设置"这条硬约束，所以整棵子树在本页只读。
     */
    private static final String LOCKED_ROOT_CODE = "system";

    private final SystemMenuMapper menuMapper;
    private final SystemRoleMenuMapper roleMenuMapper;
    private final RoleMapper roleMapper;

    public SystemNavigationServiceImpl(SystemMenuMapper menuMapper, SystemRoleMenuMapper roleMenuMapper,
                                       RoleMapper roleMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<NavigationView> listNavigation() {
        List<SystemMenuEntity> nodes = loadNavigableNodes();
        Set<Long> lockedIds = lockedSubtreeIds(nodes);
        Map<Long, Long> grantedCount = grantedRoleCountByMenu();
        return nodes.stream()
            .map(node -> toView(node, lockedIds, grantedCount))
            .toList();
    }

    @Override
    @Transactional
    public List<NavigationView> saveNavigation(NavigationUpdateRequest request) {
        List<SystemMenuEntity> nodes = loadNavigableNodes();
        Map<Long, SystemMenuEntity> nodeById = nodes.stream()
            .collect(Collectors.toMap(SystemMenuEntity::getId, node -> node, (left, right) -> left));
        Set<Long> lockedIds = lockedSubtreeIds(nodes);

        Set<Long> touchedIds = new HashSet<>();
        // 先整批校验再落库：半批生效会让页面与库里的结构对不上，而这种对不上没有任何提示
        List<SystemMenuEntity> pending = new ArrayList<>();
        for (NavigationNodeUpdateRequest change : request.nodes()) {
            if (!touchedIds.add(change.id())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "同一个分组或页面被提交了两次：" + change.id());
            }
            SystemMenuEntity entity = nodeById.get(change.id());
            if (entity == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "菜单节点不存在或不属于管理端：" + change.id());
            }
            if (lockedIds.contains(entity.getId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "「" + entity.getMenuName() + "」属于系统设置，本页不能调整，请到「菜单管理」维护");
            }
            String menuName = requireText(change.menuName(), "名称");
            validateParent(change, entity, nodeById, lockedIds);
            entity.setMenuName(menuName);
            entity.setIcon(blankToNull(change.icon()));
            entity.setSortNo(change.sortNo());
            entity.setParentId(change.parentId());
            entity.setVisible(change.visible());
            pending.add(entity);
        }
        assertTopLevelNamesUnique(nodes);
        // 只写这五列。父级要能被置成 NULL（提升为顶层分组），靠 SystemMenuEntity 上的
        // FieldStrategy.ALWAYS 才落得进去，updateById 默认会忽略 null 字段
        for (SystemMenuEntity entity : pending) {
            menuMapper.updateById(entity);
        }
        // 结构改动不踢会话：导航树是每次进系统现拉的（/auth/current/menus），刷新即生效。
        // 这里没碰权限码，也没有"旧权限在令牌过期前继续可用"的问题
        return listNavigation();
    }

    @Override
    @Transactional
    public NavigationCatalogCreateResultView createCatalog(NavigationCatalogCreateRequest request) {
        List<SystemMenuEntity> nodes = loadNavigableNodes();
        Set<Long> lockedIds = lockedSubtreeIds(nodes);
        String menuName = requireText(request.menuName(), "分组名称");
        long id = IdWorker.getId();
        String customCode = blankToNull(request.menuCode());
        SystemMenuEntity entity = new SystemMenuEntity();
        entity.setId(id);
        entity.setPortal(ADMIN_PORTAL);
        entity.setParentId(null);
        entity.setMenuCode(customCode == null ? "nav_" + id : customCode);
        entity.setMenuName(menuName);
        entity.setMenuType(TYPE_CATALOG);
        // 目录不带路由、不挂权限码（AGENTS「权限码与菜单节点的分配规则」第 3 条）：
        // 建完是个空分组，要放页面还得再拖，不会出现"看得见却 403"的入口
        entity.setPath(null);
        entity.setComponent(null);
        entity.setPermissionCode(null);
        entity.setIcon(blankToNull(request.icon()));
        entity.setSortNo(nextTopLevelSortNo(nodes, lockedIds));
        entity.setVisible(Boolean.TRUE);
        entity.setStatus(ACTIVE);
        entity.setRemark("由「导航编排」页新建的顶层分组；目录不挂权限码，入口显隐由子页面节点决定");
        entity.setDeleted(Boolean.FALSE);
        List<SystemMenuEntity> projected = new ArrayList<>(nodes);
        projected.add(entity);
        assertTopLevelNamesUnique(projected);
        try {
            menuMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分组编码已存在：" + entity.getMenuCode());
        }

        // 同批授权给所有在职角色。漏了这一步，新分组下面的页面入口整块不显示且完全不报错：
        // 导航树是按角色已授权节点拼的，子页的父目录没被勾到时会被提升到根、变成一颗孤零零的图标
        List<Long> roleIds = activeRoleIds();
        for (Long roleId : roleIds) {
            SystemRoleMenuEntity relation = new SystemRoleMenuEntity();
            relation.setRoleId(roleId);
            relation.setMenuId(id);
            relation.setDeleted(Boolean.FALSE);
            roleMenuMapper.insert(relation);
        }
        return new NavigationCatalogCreateResultView(id, roleIds.size(), listNavigation());
    }

    /** 管理端可编排的节点：目录 + 页面，不含按钮（按钮不进导航），也不含其他端的小程序节点 */
    private List<SystemMenuEntity> loadNavigableNodes() {
        return menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
                .eq(SystemMenuEntity::getPortal, ADMIN_PORTAL)
                .eq(SystemMenuEntity::getStatus, ACTIVE)
                .ne(SystemMenuEntity::getMenuType, TYPE_BUTTON))
            .stream()
            .sorted(Comparator.comparing((SystemMenuEntity node) -> node.getSortNo() == null ? 0 : node.getSortNo())
                .thenComparing(SystemMenuEntity::getMenuCode))
            .toList();
    }

    /** 「系统设置」整棵子树的 id（含它自己）——本页只读的那一段 */
    private Set<Long> lockedSubtreeIds(List<SystemMenuEntity> nodes) {
        Set<Long> locked = new HashSet<>();
        nodes.stream()
            .filter(node -> LOCKED_ROOT_CODE.equals(node.getMenuCode()))
            .findFirst()
            .ifPresent(root -> locked.add(root.getId()));
        if (locked.isEmpty()) {
            return locked;
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SystemMenuEntity node : nodes) {
                if (node.getParentId() != null && locked.contains(node.getParentId()) && locked.add(node.getId())) {
                    changed = true;
                }
            }
        }
        return locked;
    }

    /** 每个节点被多少个在职角色勾到——目录为 0 就是"入口整块消失"的那个坑 */
    private Map<Long, Long> grantedRoleCountByMenu() {
        Set<Long> activeRoleIds = new HashSet<>(activeRoleIds());
        if (activeRoleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Long>> roleIdsByMenu = new HashMap<>();
        for (SystemRoleMenuEntity relation : roleMenuMapper.selectList(new LambdaQueryWrapper<SystemRoleMenuEntity>())) {
            if (!activeRoleIds.contains(relation.getRoleId())) {
                continue;
            }
            roleIdsByMenu.computeIfAbsent(relation.getMenuId(), key -> new LinkedHashSet<>()).add(relation.getRoleId());
        }
        return roleIdsByMenu.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size()));
    }

    private List<Long> activeRoleIds() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getStatus, ACTIVE))
            .stream()
            .map(RoleEntity::getId)
            .toList();
    }

    /**
     * 归属校验。判据只有一条：改完客户还能不能找到这一页。
     * 因此只拦三件事——挂到不能装页面的节点上、挂进只读的底座段、把目录套进目录。
     */
    private void validateParent(NavigationNodeUpdateRequest change, SystemMenuEntity entity,
                                Map<Long, SystemMenuEntity> nodeById, Set<Long> lockedIds) {
        Long parentId = change.parentId();
        if (parentId == null) {
            return;
        }
        if (parentId.equals(entity.getId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "「" + entity.getMenuName() + "」不能挂到自己下面");
        }
        SystemMenuEntity parent = nodeById.get(parentId);
        if (parent == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "要挂进去的分组不存在或不是管理端节点：" + parentId);
        }
        if (lockedIds.contains(parentId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "不能把「" + entity.getMenuName() + "」挪进系统设置，那一段在本页只读");
        }
        if (!TYPE_CATALOG.equals(parent.getMenuType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "「" + parent.getMenuName() + "」是页面不是分组，页面只能挂在分组下面");
        }
        if (parent.getParentId() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "「" + parent.getMenuName() + "」已经在分组里了，侧边栏只到两层");
        }
        if (TYPE_CATALOG.equals(entity.getMenuType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "「" + entity.getMenuName() + "」是顶层分组，不能再套进另一个分组；侧边栏只到两层");
        }
    }

    /**
     * 顶层分组不许同名。侧边栏是"一个图标=一类事"，两颗同名图标把人刚建立的直觉又打回去，
     * 而这正是这批改动要解决的那个投诉（"菜单一堆、不知道点哪个"）。
     * 传进来的是**改完之后**的节点集合：保存路径上 entities 已就地更新，新建路径上把新行一起带上。
     */
    private void assertTopLevelNamesUnique(List<SystemMenuEntity> nodes) {
        Map<String, String> seenNames = new HashMap<>();
        for (SystemMenuEntity node : nodes) {
            if (node.getParentId() != null || node.getMenuName() == null) {
                continue;
            }
            String name = node.getMenuName().trim();
            String previous = seenNames.putIfAbsent(name.toLowerCase(), name);
            if (previous != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "顶层名称重复：「" + previous + "」。侧边栏会出现两个同名入口，请给其中一个换名字");
            }
        }
    }

    /**
     * 新分组排在业务段末尾：取未锁定的顶层节点里最大的顺序号往后加 10。
     * 顶层页面节点也算——它在侧边栏自成一组，占同一个位置序列，只比目录会让新组插到它前面。
     */
    private int nextTopLevelSortNo(List<SystemMenuEntity> nodes, Set<Long> lockedIds) {
        int max = nodes.stream()
            .filter(node -> node.getParentId() == null)
            .filter(node -> !lockedIds.contains(node.getId()))
            .mapToInt(node -> node.getSortNo() == null ? 0 : node.getSortNo())
            .max()
            .orElse(0);
        return max + 10;
    }

    private NavigationView toView(SystemMenuEntity node, Set<Long> lockedIds, Map<Long, Long> grantedCount) {
        return new NavigationView(
            node.getId(),
            node.getParentId(),
            node.getMenuCode(),
            node.getMenuName(),
            node.getMenuType(),
            node.getPath(),
            node.getPermissionCode(),
            node.getIcon(),
            node.getSortNo(),
            node.getVisible(),
            lockedIds.contains(node.getId()),
            grantedCount.getOrDefault(node.getId(), 0L)
        );
    }

    private String requireText(String value, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, field + "不能为空");
        }
        return text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
