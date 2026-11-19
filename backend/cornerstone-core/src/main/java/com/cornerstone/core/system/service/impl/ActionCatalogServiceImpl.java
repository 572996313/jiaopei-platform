package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.action.ActionCatalogContributor;
import com.cornerstone.core.system.action.ActionDefinition;
import com.cornerstone.core.system.action.ActionMode;
import com.cornerstone.core.system.domain.SystemMenuEntity;
import com.cornerstone.core.system.domain.SystemRoleMenuEntity;
import com.cornerstone.core.system.dto.ActionCatalogActionView;
import com.cornerstone.core.system.dto.ActionCatalogGapView;
import com.cornerstone.core.system.dto.ActionCatalogPageView;
import com.cornerstone.core.system.dto.ActionCatalogView;
import com.cornerstone.core.system.mapper.SystemMenuMapper;
import com.cornerstone.core.system.mapper.SystemRoleMenuMapper;
import com.cornerstone.core.system.service.ActionCatalogService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ActionCatalogServiceImpl implements ActionCatalogService {

    private static final String PORTAL_ADMIN = "admin";
    private static final String TYPE_MENU = "menu";
    private static final String TYPE_BUTTON = "button";
    private static final String ACTIVE = "active";

    private final SystemMenuMapper menuMapper;
    private final SystemRoleMenuMapper roleMenuMapper;
    private final RoleMapper roleMapper;
    /** 各模块代码登记的动作；模块未装配时对应实现不存在，目录自然少一段 */
    private final List<ActionCatalogContributor> contributors;

    public ActionCatalogServiceImpl(SystemMenuMapper menuMapper,
                                    SystemRoleMenuMapper roleMenuMapper,
                                    RoleMapper roleMapper,
                                    List<ActionCatalogContributor> contributors) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleMapper = roleMapper;
        this.contributors = contributors;
    }

    @Override
    public ActionCatalogView catalog(Long roleId) {
        List<SystemMenuEntity> menus = menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
            .eq(SystemMenuEntity::getPortal, PORTAL_ADMIN));
        Map<String, SystemMenuEntity> menuByCode = menus.stream()
            .collect(Collectors.toMap(SystemMenuEntity::getMenuCode, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));

        List<SystemMenuEntity> pages = menus.stream()
            .filter(menu -> TYPE_MENU.equals(menu.getMenuType()))
            .filter(menu -> menu.getStatus() == null || ACTIVE.equalsIgnoreCase(menu.getStatus()))
            .sorted(Comparator.comparing((SystemMenuEntity menu) -> menu.getSortNo() == null ? 0 : menu.getSortNo())
                .thenComparing(SystemMenuEntity::getMenuCode))
            .toList();

        RoleGrantState grantState = roleId == null ? RoleGrantState.empty() : loadGrantState(roleId);

        List<ActionCatalogGapView> gaps = new ArrayList<>();
        Map<String, List<ActionDefinition>> definitionsByPage = new LinkedHashMap<>();
        Set<String> seenActionKeys = new HashSet<>();
        for (ActionCatalogContributor contributor : contributors) {
            List<ActionDefinition> definitions = contributor.actions() == null ? List.of() : contributor.actions();
            for (ActionDefinition definition : definitions) {
                validateDefinition(contributor.moduleKey(), definition, menuByCode, seenActionKeys, gaps);
                definitionsByPage.computeIfAbsent(definition.pageMenuCode(), key -> new ArrayList<>())
                    .add(definition);
            }
        }

        List<ActionCatalogPageView> pageViews = new ArrayList<>();
        for (SystemMenuEntity page : pages) {
            List<ActionDefinition> definitions = definitionsByPage.getOrDefault(page.getMenuCode(), List.of())
                .stream()
                .sorted(Comparator.comparingInt(ActionDefinition::sortNo)
                    .thenComparing(ActionDefinition::actionKey))
                .toList();
            List<ActionCatalogActionView> actions = definitions.stream()
                .map(definition -> toActionView(definition, menuByCode, grantState))
                .toList();
            pageViews.add(new ActionCatalogPageView(
                page.getId(),
                page.getMenuCode(),
                page.getMenuName(),
                page.getPath(),
                blankToNull(page.getPermissionCode()),
                actions
            ));
        }
        return new ActionCatalogView(pageViews, gaps);
    }

    @Override
    public Set<Long> managedMenuIds() {
        Set<Long> ids = new LinkedHashSet<>();
        List<SystemMenuEntity> menus = menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
            .eq(SystemMenuEntity::getPortal, PORTAL_ADMIN));
        Map<String, SystemMenuEntity> menuByCode = menus.stream()
            .collect(Collectors.toMap(SystemMenuEntity::getMenuCode, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
        for (SystemMenuEntity menu : menus) {
            if (TYPE_MENU.equals(menu.getMenuType())
                && (menu.getStatus() == null || ACTIVE.equalsIgnoreCase(menu.getStatus()))) {
                ids.add(menu.getId());
            }
        }
        for (ActionDefinition definition : allDefinitions()) {
            if (definition.mode() == ActionMode.UI_ONLY || definition.grantMenuCode() == null) {
                continue;
            }
            SystemMenuEntity grant = menuByCode.get(definition.grantMenuCode());
            if (grant != null) {
                ids.add(grant.getId());
            }
        }
        return ids;
    }

    @Override
    public Map<String, Long> pageMenuIdByActionKey() {
        List<SystemMenuEntity> menus = menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
            .eq(SystemMenuEntity::getPortal, PORTAL_ADMIN));
        Map<String, SystemMenuEntity> menuByCode = menus.stream()
            .collect(Collectors.toMap(SystemMenuEntity::getMenuCode, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
        Map<String, Long> result = new HashMap<>();
        for (ActionDefinition definition : allDefinitions()) {
            SystemMenuEntity page = menuByCode.get(definition.pageMenuCode());
            if (page != null) {
                result.put(definition.actionKey(), page.getId());
            }
        }
        return result;
    }

    @Override
    public Map<Long, Long> pageMenuIdByGrantMenuId() {
        List<SystemMenuEntity> menus = menuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
            .eq(SystemMenuEntity::getPortal, PORTAL_ADMIN));
        Map<String, SystemMenuEntity> menuByCode = menus.stream()
            .collect(Collectors.toMap(SystemMenuEntity::getMenuCode, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
        Map<Long, Long> result = new HashMap<>();
        for (ActionDefinition definition : allDefinitions()) {
            if (definition.mode() == ActionMode.UI_ONLY || definition.grantMenuCode() == null) {
                continue;
            }
            SystemMenuEntity page = menuByCode.get(definition.pageMenuCode());
            SystemMenuEntity grant = menuByCode.get(definition.grantMenuCode());
            if (page != null && grant != null) {
                result.put(grant.getId(), page.getId());
            }
        }
        return result;
    }

    @Override
    public List<ActionDefinition> allDefinitions() {
        return contributors.stream()
            .filter(contributor -> contributor.actions() != null)
            .flatMap(contributor -> contributor.actions().stream())
            .toList();
    }

    private void validateDefinition(String moduleKey, ActionDefinition definition,
                                    Map<String, SystemMenuEntity> menuByCode,
                                    Set<String> seenActionKeys,
                                    List<ActionCatalogGapView> gaps) {
        if (definition.actionKey() == null || definition.actionKey().isBlank()) {
            gaps.add(new ActionCatalogGapView(null, definition.pageMenuCode(), definition.grantMenuCode(),
                moduleKey + " 登记了空的动作键"));
            return;
        }
        if (!seenActionKeys.add(definition.actionKey())) {
            gaps.add(new ActionCatalogGapView(definition.actionKey(), definition.pageMenuCode(),
                definition.grantMenuCode(), "动作键重复，后登记的一条不会生效"));
        }
        if (definition.label() == null || definition.label().isBlank()) {
            gaps.add(new ActionCatalogGapView(definition.actionKey(), definition.pageMenuCode(),
                definition.grantMenuCode(), "缺少中文按钮名"));
        }
        SystemMenuEntity page = menuByCode.get(definition.pageMenuCode());
        if (page == null || !TYPE_MENU.equals(page.getMenuType())) {
            gaps.add(new ActionCatalogGapView(definition.actionKey(), definition.pageMenuCode(),
                definition.grantMenuCode(), "所属页面节点不存在或不是 menu 类型"));
        }
        if (definition.mode() == ActionMode.UI_ONLY) {
            if (definition.grantMenuCode() != null || definition.permissionCode() != null) {
                gaps.add(new ActionCatalogGapView(definition.actionKey(), definition.pageMenuCode(),
                    definition.grantMenuCode(), "纯前端动作不应绑定授权节点或权限码"));
            }
            return;
        }
        if (definition.mode() == null) {
            gaps.add(new ActionCatalogGapView(definition.actionKey(), definition.pageMenuCode(),
                definition.grantMenuCode(), "缺少授权模式"));
            return;
        }
        SystemMenuEntity grant = menuByCode.get(definition.grantMenuCode());
        if (grant == null || !TYPE_BUTTON.equals(grant.getMenuType())) {
            gaps.add(new ActionCatalogGapView(definition.actionKey(), definition.pageMenuCode(),
                definition.grantMenuCode(), "授权节点不存在或不是 button 类型"));
            return;
        }
        if (definition.permissionCode() == null || definition.permissionCode().isBlank()) {
            gaps.add(new ActionCatalogGapView(definition.actionKey(), definition.pageMenuCode(),
                definition.grantMenuCode(), "缺少权限码"));
            return;
        }
        if (!definition.permissionCode().equals(grant.getPermissionCode())) {
            gaps.add(new ActionCatalogGapView(definition.actionKey(), definition.pageMenuCode(),
                definition.grantMenuCode(), "权限码与授权节点不一致：节点为 " + grant.getPermissionCode()));
        }
    }

    private ActionCatalogActionView toActionView(ActionDefinition definition,
                                                 Map<String, SystemMenuEntity> menuByCode,
                                                 RoleGrantState grantState) {
        SystemMenuEntity grant = definition.grantMenuCode() == null
            ? null
            : menuByCode.get(definition.grantMenuCode());
        Boolean grantedByNode = null;
        Boolean grantedByCsv = null;
        if (grantState.available()) {
            grantedByNode = grant != null && grantState.menuIds().contains(grant.getId());
            grantedByCsv = definition.permissionCode() != null
                && grantState.presetCodes().contains(definition.permissionCode());
        }
        return new ActionCatalogActionView(
            definition.actionKey(),
            definition.label(),
            definition.description(),
            definition.permissionCode(),
            grant == null ? null : grant.getId(),
            definition.grantMenuCode(),
            toModeValue(definition.mode()),
            definition.sensitive(),
            definition.sortNo(),
            grantedByNode,
            grantedByCsv,
            null
        );
    }

    private RoleGrantState loadGrantState(Long roleId) {
        RoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在：" + roleId);
        }
        Set<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SystemRoleMenuEntity>()
                .eq(SystemRoleMenuEntity::getRoleId, roleId))
            .stream()
            .map(SystemRoleMenuEntity::getMenuId)
            .collect(Collectors.toCollection(HashSet::new));
        Set<String> presetCodes = new LinkedHashSet<>(splitCodes(role.getPermissionCodes()));
        return new RoleGrantState(true, menuIds, presetCodes);
    }

    private List<String> splitCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    }

    private String toModeValue(ActionMode mode) {
        if (mode == ActionMode.UI_ONLY) {
            return "uiOnly";
        }
        return mode == null ? null : mode.name().toLowerCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record RoleGrantState(boolean available, Set<Long> menuIds, Set<String> presetCodes) {
        static RoleGrantState empty() {
            return new RoleGrantState(false, Set.of(), Set.of());
        }
    }
}
