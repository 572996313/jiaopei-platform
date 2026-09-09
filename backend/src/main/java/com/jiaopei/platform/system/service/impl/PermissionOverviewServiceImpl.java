package com.jiaopei.platform.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.auth.domain.RoleEntity;
import com.jiaopei.platform.auth.mapper.RoleMapper;
import com.jiaopei.platform.system.domain.SystemMenuEntity;
import com.jiaopei.platform.system.domain.SystemRoleMenuEntity;
import com.jiaopei.platform.system.domain.entity.ListConfigEntity;
import com.jiaopei.platform.system.dto.PermissionConfigButtonRefView;
import com.jiaopei.platform.system.dto.PermissionEndpointInfoView;
import com.jiaopei.platform.system.dto.PermissionMenuNodeRefView;
import com.jiaopei.platform.system.dto.PermissionOverviewView;
import com.jiaopei.platform.system.dto.PermissionRoleRefView;
import com.jiaopei.platform.system.dto.PermissionUsageView;
import com.jiaopei.platform.system.dto.UnregisteredPermissionView;
import com.jiaopei.platform.system.mapper.ListConfigMapper;
import com.jiaopei.platform.system.mapper.SystemMenuMapper;
import com.jiaopei.platform.system.mapper.SystemRoleMenuMapper;
import com.jiaopei.platform.system.service.PermissionOverviewService;
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
                buttonsByCode.getOrDefault(code, List.of()),
                endpoints.isEmpty()
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

    private List<RoleEntity> activeRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
            .eq(RoleEntity::getStatus, ACTIVE));
    }

    /**
     * 角色对权限码的持有关系 = 角色 permission_codes CSV ∪ 角色勾选的按钮节点码。
     */
    private Map<String, List<PermissionRoleRefView>> buildRoleOwnership(List<RoleEntity> roles,
                                                                        List<SystemMenuEntity> permissionMenus) {
        Map<Long, String> codeByMenuId = permissionMenus.stream()
            .collect(Collectors.toMap(SystemMenuEntity::getId, SystemMenuEntity::getPermissionCode, (left, right) -> left));

        Map<Long, Set<String>> codesByRole = new HashMap<>();
        for (SystemRoleMenuEntity grant : systemRoleMenuMapper.selectList(null)) {
            String code = codeByMenuId.get(grant.getMenuId());
            if (code != null) {
                codesByRole.computeIfAbsent(grant.getRoleId(), key -> new HashSet<>()).add(code);
            }
        }

        Map<String, List<PermissionRoleRefView>> result = new HashMap<>();
        for (RoleEntity role : roles) {
            Set<String> codes = new HashSet<>(splitPermissionCodes(role.getPermissionCodes()));
            codes.addAll(codesByRole.getOrDefault(role.getId(), Set.of()));
            for (String code : codes) {
                result.computeIfAbsent(code, key -> new ArrayList<>())
                    .add(new PermissionRoleRefView(role.getId(), role.getRoleCode(), role.getRoleName()));
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
