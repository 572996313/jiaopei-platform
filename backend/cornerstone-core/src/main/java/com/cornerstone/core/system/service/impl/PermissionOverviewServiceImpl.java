package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.system.domain.SystemMenuEntity;
import com.cornerstone.core.system.domain.SystemRoleMenuEntity;
import com.cornerstone.core.system.domain.entity.ListConfigEntity;
import com.cornerstone.core.system.dto.PermissionConfigButtonRefView;
import com.cornerstone.core.system.dto.PermissionEndpointInfoView;
import com.cornerstone.core.system.dto.PermissionMenuNodeRefView;
import com.cornerstone.core.system.dto.PermissionOptionView;
import com.cornerstone.core.system.dto.PermissionOverviewView;
import com.cornerstone.core.system.dto.PermissionRoleRefView;
import com.cornerstone.core.system.dto.PermissionUsageView;
import com.cornerstone.core.system.dto.UnregisteredPermissionView;
import com.cornerstone.core.system.mapper.ListConfigMapper;
import com.cornerstone.core.system.mapper.SystemMenuMapper;
import com.cornerstone.core.system.mapper.SystemRoleMenuMapper;
import com.cornerstone.core.system.service.PermissionOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class PermissionOverviewServiceImpl implements PermissionOverviewService {

    private static final String ACTIVE = "active";

    private final RequestMappingHandlerMapping handlerMapping;
    private final RoleMapper roleMapper;
    private final SystemMenuMapper systemMenuMapper;
    private final SystemRoleMenuMapper systemRoleMenuMapper;
    private final ListConfigMapper listConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * 注解扫描结果缓存。懒初始化:首次请求时执行,
     * 避免容器启动早期 RequestMappingHandlerMapping 尚未完成 handler 注册的时序问题。
     */
    private volatile Map<String, List<PermissionEndpointInfoView>> registryCache;

    public PermissionOverviewServiceImpl(RequestMappingHandlerMapping handlerMapping,
                                         RoleMapper roleMapper,
                                         SystemMenuMapper systemMenuMapper,
                                         SystemRoleMenuMapper systemRoleMenuMapper,
                                         ListConfigMapper listConfigMapper,
                                         ObjectMapper objectMapper) {
        this.handlerMapping = handlerMapping;
        this.roleMapper = roleMapper;
        this.systemMenuMapper = systemMenuMapper;
        this.systemRoleMenuMapper = systemRoleMenuMapper;
        this.listConfigMapper = listConfigMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PermissionOverviewView overview() {
        Map<String, List<PermissionEndpointInfoView>> registry = scanRegistry();

        List<SystemMenuEntity> permissionMenus = systemMenuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
            .isNotNull(SystemMenuEntity::getPermissionCode)
            .ne(SystemMenuEntity::getPermissionCode, ""));
        Map<String, List<PermissionMenuNodeRefView>> menuNodesByCode = permissionMenus.stream()
            .collect(Collectors.groupingBy(SystemMenuEntity::getPermissionCode,
                Collectors.mapping(this::toMenuRef, Collectors.toList())));

        Map<String, List<PermissionRoleRefView>> rolesByCode = buildRoleOwnership(activeRoles(), permissionMenus);

        Map<String, List<PermissionConfigButtonRefView>> buttonsByCode = buildConfigButtonUsage();

        // 以注解注册表为权威源组装已登记权限码
        List<PermissionUsageView> permissions = new ArrayList<>();
        for (Map.Entry<String, List<PermissionEndpointInfoView>> entry : registry.entrySet()) {
            String code = entry.getKey();
            List<PermissionEndpointInfoView> endpoints = entry.getValue();
            permissions.add(new PermissionUsageView(
                code,
                endpoints,
                rolesByCode.getOrDefault(code, List.of()),
                menuNodesByCode.getOrDefault(code, List.of()),
                buttonsByCode.getOrDefault(code, List.of())
            ));
        }
        permissions.sort(Comparator.comparing(PermissionUsageView::code));

        // 未登记码:被菜单节点/角色/按钮配置引用,但后端没有任何接口使用
        Set<String> allReferencedCodes = new TreeSet<>();
        allReferencedCodes.addAll(menuNodesByCode.keySet());
        allReferencedCodes.addAll(rolesByCode.keySet());
        allReferencedCodes.addAll(buttonsByCode.keySet());
        List<UnregisteredPermissionView> unregistered = allReferencedCodes.stream()
            .filter(code -> !registry.containsKey(code))
            .map(code -> new UnregisteredPermissionView(
                code,
                rolesByCode.getOrDefault(code, List.of()),
                menuNodesByCode.getOrDefault(code, List.of()),
                buttonsByCode.getOrDefault(code, List.of())))
            .toList();

        return new PermissionOverviewView(permissions, unregistered);
    }

    @Override
    public List<PermissionOptionView> permissionOptions() {
        Map<String, List<PermissionEndpointInfoView>> registry = scanRegistry();

        // 启用且带权限码的菜单节点:权限码 → 中文名列表。按钮节点优先,其次页面节点
        // (读取码挂在页面节点上,只查按钮会让下拉退化成裸权限码)
        Map<String, List<String>> menuNamesByCode = systemMenuMapper.selectList(new LambdaQueryWrapper<SystemMenuEntity>()
                .eq(SystemMenuEntity::getStatus, ACTIVE)
                .isNotNull(SystemMenuEntity::getPermissionCode)
                .ne(SystemMenuEntity::getPermissionCode, ""))
            .stream()
            .sorted(Comparator.comparing((SystemMenuEntity menu) -> !"button".equals(menu.getMenuType()))
                .thenComparing(SystemMenuEntity::getId))
            .collect(Collectors.groupingBy(SystemMenuEntity::getPermissionCode,
                Collectors.mapping(SystemMenuEntity::getMenuName, Collectors.toList())));

        // 以注解扫描注册表为权威源:每个注册权限码生成一个选项,label 优先按钮中文名,其次码本身
        return registry.keySet().stream()
            .map(code -> {
                List<String> menuNames = menuNamesByCode.getOrDefault(code, List.of());
                String label = menuNames.isEmpty() ? code : menuNames.get(0);
                return new PermissionOptionView(code, label, menuNames);
            })
            .sorted(Comparator.comparing(PermissionOptionView::code))
            .toList();
    }

    private List<RoleEntity> activeRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
            .eq(RoleEntity::getStatus, ACTIVE));
    }

    /**
     * 角色对权限码的持有关系 = 角色 permission_codes CSV ∪ 角色勾选的携带该码的菜单节点。
     * 两条来源分别标出:总览页要据此提示"撤销时两边都得撤"。
     */
    private Map<String, List<PermissionRoleRefView>> buildRoleOwnership(List<RoleEntity> roles,
                                                                        List<SystemMenuEntity> permissionMenus) {
        Map<Long, String> codeByMenuId = permissionMenus.stream()
            .collect(Collectors.toMap(SystemMenuEntity::getId, SystemMenuEntity::getPermissionCode, (left, right) -> left));

        Map<Long, Set<String>> nodeCodesByRole = new HashMap<>();
        for (SystemRoleMenuEntity grant : systemRoleMenuMapper.selectList(null)) {
            String code = codeByMenuId.get(grant.getMenuId());
            if (code != null) {
                nodeCodesByRole.computeIfAbsent(grant.getRoleId(), key -> new HashSet<>()).add(code);
            }
        }

        Map<String, List<PermissionRoleRefView>> result = new HashMap<>();
        for (RoleEntity role : roles) {
            Set<String> presetCodes = new HashSet<>(splitPermissionCodes(role.getPermissionCodes()));
            Set<String> menuCodes = nodeCodesByRole.getOrDefault(role.getId(), Set.of());
            Set<String> heldCodes = new TreeSet<>(presetCodes);
            heldCodes.addAll(menuCodes);
            for (String code : heldCodes) {
                result.computeIfAbsent(code, key -> new ArrayList<>())
                    .add(new PermissionRoleRefView(role.getId(), role.getRoleCode(), role.getRoleName(),
                        presetCodes.contains(code), menuCodes.contains(code)));
            }
        }
        return result;
    }

    private Map<String, List<PermissionConfigButtonRefView>> buildConfigButtonUsage() {
        Map<String, List<PermissionConfigButtonRefView>> result = new HashMap<>();
        for (ListConfigEntity config : listConfigMapper.selectList(null)) {
            String actionsJson = config.getActionsJson();
            if (actionsJson == null || actionsJson.isBlank()) {
                continue;
            }
            try {
                List<Map<String, Object>> actions = objectMapper.readValue(actionsJson, new TypeReference<>() {
                });
                for (Map<String, Object> action : actions) {
                    if (!(action.get("permission") instanceof String code) || code.isBlank()) {
                        continue;
                    }
                    result.computeIfAbsent(code, key -> new ArrayList<>()).add(new PermissionConfigButtonRefView(
                        config.getResourceCode(),
                        config.getResourceName(),
                        text(action.get("action")),
                        text(action.get("label")),
                        text(action.get("position"))));
                }
            } catch (JsonProcessingException ignored) {
                // 单条配置 JSON 非法时跳过,不影响总览其余数据;配置保存入口已有校验
            }
        }
        return result;
    }

    /**
     * 扫描全部 Controller 的 @RequiresPermission 注解,构建 权限码 → 接口清单 注册表。
     * 方法级注解优先,其次类级;与 PermissionGuardAspect 的校验顺序保持一致。
     */
    private Map<String, List<PermissionEndpointInfoView>> scanRegistry() {
        Map<String, List<PermissionEndpointInfoView>> cached = registryCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (registryCache == null) {
                Map<String, List<PermissionEndpointInfoView>> registry = new TreeMap<>();
                for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
                    HandlerMethod handler = entry.getValue();
                    RequiresPermission annotation = handler.getMethodAnnotation(RequiresPermission.class);
                    if (annotation == null) {
                        annotation = handler.getBeanType().getAnnotation(RequiresPermission.class);
                    }
                    if (annotation == null) {
                        continue;
                    }
                    registry.computeIfAbsent(annotation.value(), key -> new ArrayList<>())
                        .add(toEndpointInfo(entry.getKey(), handler));
                }
                registryCache = registry;
            }
            return registryCache;
        }
    }

    private PermissionEndpointInfoView toEndpointInfo(RequestMappingInfo info, HandlerMethod handler) {
        RequestMethodsRequestCondition methodsCondition = info.getMethodsCondition();
        String method = methodsCondition.getMethods().isEmpty()
            ? "ANY"
            : methodsCondition.getMethods().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        PathPatternsRequestCondition pathPatterns = info.getPathPatternsCondition();
        String path = pathPatterns == null ? "" : String.join(",", pathPatterns.getPatternValues());
        Operation operation = handler.getMethodAnnotation(Operation.class);
        return new PermissionEndpointInfoView(method, path, operation == null ? null : operation.summary());
    }

    private PermissionMenuNodeRefView toMenuRef(SystemMenuEntity entity) {
        return new PermissionMenuNodeRefView(entity.getId(), entity.getMenuName(), entity.getMenuCode());
    }

    private List<String> splitPermissionCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }

    private String text(Object value) {
        return value instanceof String string ? string : null;
    }
}
