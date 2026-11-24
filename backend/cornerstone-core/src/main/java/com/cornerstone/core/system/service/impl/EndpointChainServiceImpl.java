package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.system.action.ActionDefinition;
import com.cornerstone.core.system.dto.EndpointChainActionView;
import com.cornerstone.core.system.dto.EndpointChainEndpointView;
import com.cornerstone.core.system.dto.EndpointChainServiceView;
import com.cornerstone.core.system.dto.EndpointChainTableView;
import com.cornerstone.core.system.dto.EndpointChainView;
import com.cornerstone.core.system.dto.SchemaTableView;
import com.cornerstone.core.system.service.ActionCatalogService;
import com.cornerstone.core.system.service.EndpointChainService;
import com.cornerstone.core.system.service.SchemaDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 接口链路实现：从 Spring MVC 的 HandlerMapping 出发，顺着控制器/服务的字段声明往下走。
 *
 * <p>三条边界写死在实现里，避免页面被误读成"执行记录"：</p>
 * <ol>
 *   <li><b>类级依赖，不是方法级调用</b>：控制器注入了哪些服务、服务注入了哪些 Mapper，
 *       就认为这条链路"可能涉及"这些表。同一服务上的多个接口会看到同一批表。</li>
 *   <li><b>只跟随命名约定</b>：沿类型名以 {@code Service} 结尾的字段继续向下，
 *       其它组件（工具类、事件发布器）不展开，避免整张图变成蜘蛛网。</li>
 *   <li><b>底座不认识业务</b>：模块归属由包名推导，表注释取数据字典，全部靠反射完成，
 *       本类不 import 任何业务包。</li>
 * </ol>
 */
@Service
public class EndpointChainServiceImpl implements EndpointChainService {

    private static final Logger log = LoggerFactory.getLogger(EndpointChainServiceImpl.class);

    /** 接口与表结构都不会每秒变；与数据字典保持同一个缓存窗口 */
    private static final long CACHE_TTL_MILLIS = 60_000L;

    private static final int MAX_DEPTH = 3;
    private static final String OTHER_MODULE = "other";
    private static final String FALLBACK_BASE_PACKAGE = "com.cornerstone";
    /** 页面与接口共用的一句口径；测试锁死关键字，防止后人把链路改成"执行记录" */
    static final String NOTE =
        "链路是代码里的声明依赖（控制器/服务持有哪些字段），不是某一次请求的实际调用记录；"
            + "表只表示可能涉及，不区分本次读了还是写了。";

    private final RequestMappingHandlerMapping handlerMapping;
    private final ApplicationContext applicationContext;
    private final ActionCatalogService actionCatalogService;
    private final SchemaDictionaryService schemaDictionaryService;

    private final Map<Class<?>, Deps> depsCache = new HashMap<>();

    private volatile List<String> basePackages;
    private volatile EndpointChainView viewCache;
    private volatile long viewCacheAt;

    public EndpointChainServiceImpl(RequestMappingHandlerMapping handlerMapping,
                                    ApplicationContext applicationContext,
                                    ActionCatalogService actionCatalogService,
                                    SchemaDictionaryService schemaDictionaryService) {
        this.handlerMapping = handlerMapping;
        this.applicationContext = applicationContext;
        this.actionCatalogService = actionCatalogService;
        this.schemaDictionaryService = schemaDictionaryService;
    }

    @Override
    public EndpointChainView chains() {
        long now = System.currentTimeMillis();
        EndpointChainView cached = viewCache;
        if (cached != null && now - viewCacheAt < CACHE_TTL_MILLIS) {
            return cached;
        }
        synchronized (this) {
            if (viewCache != null && System.currentTimeMillis() - viewCacheAt < CACHE_TTL_MILLIS) {
                return viewCache;
            }
            EndpointChainView fresh = load();
            viewCache = fresh;
            viewCacheAt = System.currentTimeMillis();
            return fresh;
        }
    }

    private EndpointChainView load() {
        Map<String, List<EndpointChainActionView>> actionsByPermission = actionsByPermission();
        Map<String, String> tableComments = tableComments();

        List<EndpointChainEndpointView> endpoints = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            Class<?> beanType = handler.getBeanType();
            if (!isPlatformType(beanType)) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            String permissionCode = permissionCodeOf(handler);
            Deps deps = depsOf(handler);
            endpoints.add(new EndpointChainEndpointView(
                httpMethod(info),
                pathOf(info),
                operationSummary(handler),
                operationDescription(handler),
                permissionCode,
                moduleKeyOf(beanType.getName()),
                beanType.getSimpleName(),
                handler.getMethod().getName(),
                actionsByPermission.getOrDefault(permissionCode, List.of()),
                deps.services(),
                toTables(deps.tables(), tableComments)
            ));
        }
        endpoints.sort(Comparator
            .comparingInt((EndpointChainEndpointView endpoint) -> moduleOrder(endpoint.moduleKey()))
            .thenComparing(EndpointChainEndpointView::moduleKey)
            .thenComparing(EndpointChainEndpointView::path)
            .thenComparing(EndpointChainEndpointView::method));

        int permissionCount = (int) endpoints.stream()
            .filter(endpoint -> endpoint.permissionCode() != null && !endpoint.permissionCode().isBlank())
            .count();
        return new EndpointChainView(
            OffsetDateTime.now().toString(),
            NOTE,
            endpoints.size(),
            permissionCount,
            endpoints);
    }

    // ------------------------------------------------------------------ 依赖推导

    private Deps depsOf(HandlerMethod handler) {
        Class<?> controllerClass;
        try {
            // getHandlerMethods() 里的 HandlerMethod 可能是"懒解析"形态：getBean() 返回的是
            // bean 名字符串，必须先 createWithResolvedBean() 才能拿到控制器实例
            controllerClass = AopUtils.getTargetClass(handler.createWithResolvedBean().getBean());
        } catch (RuntimeException ex) {
            controllerClass = handler.getBeanType();
        }
        synchronized (depsCache) {
            return depsCache.computeIfAbsent(controllerClass, this::buildDeps);
        }
    }

    private Deps buildDeps(Class<?> controllerClass) {
        Map<String, EndpointChainServiceView> services = new LinkedHashMap<>();
        Map<String, TableRef> tables = new LinkedHashMap<>();
        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(controllerClass);

        int depth = 0;
        while (!queue.isEmpty() && depth < MAX_DEPTH) {
            int levelSize = queue.size();
            for (int index = 0; index < levelSize; index++) {
                Class<?> current = queue.poll();
                if (current == null || !visited.add(current)) {
                    continue;
                }
                for (Field field : instanceFields(current)) {
                    Class<?> type = field.getType();
                    if (!isPlatformType(type)) {
                        continue;
                    }
                    if (BaseMapper.class.isAssignableFrom(type)) {
                        TableRef ref = tableRefOf(type);
                        if (ref != null) {
                            tables.putIfAbsent(ref.tableName(), ref);
                        }
                        continue;
                    }
                    if (!looksLikeService(type)) {
                        continue;
                    }
                    for (Object bean : beansOf(type)) {
                        Class<?> target = AopUtils.getTargetClass(bean);
                        services.putIfAbsent(target.getName(), new EndpointChainServiceView(
                            type.getSimpleName(), target.getSimpleName(), moduleKeyOf(target.getName())));
                        queue.add(target);
                    }
                }
            }
            depth++;
        }

        List<EndpointChainServiceView> sortedServices = services.values().stream()
            .sorted(Comparator.comparing(EndpointChainServiceView::className))
            .toList();
        List<TableRef> sortedTables = tables.values().stream()
            .sorted(Comparator.comparing(TableRef::tableName))
            .toList();
        return new Deps(sortedServices, sortedTables);
    }

    private List<Object> beansOf(Class<?> type) {
        try {
            return applicationContext.getBeanProvider(type).orderedStream()
                .<Object>map(bean -> bean)
                .toList();
        } catch (RuntimeException ex) {
            log.debug("接口链路跳过无法解析的依赖类型 {}", type.getName(), ex);
            return List.of();
        }
    }

    private static Iterable<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                    continue;
                }
                fields.add(field);
            }
        }
        return fields;
    }

    /** 只沿命名约定展开服务；工具类、事件发布器不展开，否则链路会变成全图 */
    static boolean looksLikeService(Class<?> type) {
        return type.getSimpleName().endsWith("Service");
    }

    /**
     * Mapper 泛型 → 实体 → 表名。自定义 Mapper 只要最终继承 {@link BaseMapper} 就能解析；
     * 解析不到时返回 null，链路里少一行，不会抛错。
     */
    static Class<?> entityTypeOf(Class<?> mapperType) {
        return ResolvableType.forClass(mapperType).as(BaseMapper.class).getGeneric(0).resolve();
    }

    static String tableNameOf(Class<?> entityClass) {
        TableName tableName = entityClass.getAnnotation(TableName.class);
        if (tableName != null && !tableName.value().isBlank()) {
            return tableName.value();
        }
        return SchemaDictionaryServiceImpl.camelToUnderline(entityClass.getSimpleName());
    }

    private TableRef tableRefOf(Class<?> mapperType) {
        Class<?> entityClass = entityTypeOf(mapperType);
        if (entityClass == null) {
            return null;
        }
        return new TableRef(tableNameOf(entityClass), mapperType.getSimpleName(),
            moduleKeyOf(entityClass.getName()));
    }

    private List<EndpointChainTableView> toTables(List<TableRef> tables, Map<String, String> comments) {
        return tables.stream()
            .map(table -> new EndpointChainTableView(
                table.tableName(),
                comments.getOrDefault(table.tableName(), ""),
                table.moduleKey(),
                table.mapperClass()))
            .toList();
    }

    // ------------------------------------------------------------------ 权限与按钮

    private Map<String, List<EndpointChainActionView>> actionsByPermission() {
        Map<String, List<EndpointChainActionView>> result = new HashMap<>();
        try {
            for (ActionDefinition definition : actionCatalogService.allDefinitions()) {
                String code = definition.permissionCode();
                if (code == null || code.isBlank()) {
                    continue;
                }
                result.computeIfAbsent(code, key -> new ArrayList<>()).add(new EndpointChainActionView(
                    definition.pageMenuCode(),
                    definition.actionKey(),
                    definition.label(),
                    definition.description(),
                    definition.mode().name(),
                    definition.sensitive()));
            }
        } catch (RuntimeException ex) {
            log.warn("按钮目录读取失败，接口链路将不显示按钮", ex);
            return Map.of();
        }
        result.values().forEach(actions -> actions.sort(Comparator.comparing(EndpointChainActionView::label)));
        return result;
    }

    private static String permissionCodeOf(HandlerMethod handler) {
        RequiresPermission annotation = handler.getMethodAnnotation(RequiresPermission.class);
        if (annotation == null) {
            annotation = handler.getBeanType().getAnnotation(RequiresPermission.class);
        }
        return annotation == null ? null : annotation.value();
    }

    private static String operationSummary(HandlerMethod handler) {
        Operation operation = handler.getMethodAnnotation(Operation.class);
        return operation == null ? null : operation.summary();
    }

    private static String operationDescription(HandlerMethod handler) {
        Operation operation = handler.getMethodAnnotation(Operation.class);
        return operation == null ? null : operation.description();
    }

    // ------------------------------------------------------------------ 元数据

    private Map<String, String> tableComments() {
        Map<String, String> result = new HashMap<>();
        try {
            for (SchemaTableView table : schemaDictionaryService.dictionary().tables()) {
                result.put(table.tableName(), table.tableComment());
            }
        } catch (RuntimeException ex) {
            // 字典依赖数据库；库暂时不可用时链路仍应能打开，表注释留空即可
            log.warn("数据字典不可用，接口链路将不显示表注释", ex);
        }
        return result;
    }

    private List<String> basePackages() {
        List<String> cached = basePackages;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (basePackages == null) {
                try {
                    basePackages = AutoConfigurationPackages.get(applicationContext);
                } catch (IllegalStateException ex) {
                    log.warn("未取到自动配置基准包，接口链路按 {} 判定平台类", FALLBACK_BASE_PACKAGE, ex);
                    basePackages = List.of(FALLBACK_BASE_PACKAGE);
                }
            }
            return basePackages;
        }
    }

    private boolean isPlatformType(Class<?> type) {
        for (String basePackage : basePackages()) {
            if (type.getName().startsWith(basePackage + ".")) {
                return true;
            }
        }
        return false;
    }

    private String moduleKeyOf(String className) {
        for (String basePackage : basePackages()) {
            String key = SchemaDictionaryServiceImpl.moduleKeyOf(className, basePackage);
            if (!OTHER_MODULE.equals(key)) {
                return key;
            }
        }
        return OTHER_MODULE;
    }

    private static int moduleOrder(String moduleKey) {
        return switch (moduleKey) {
            case "core" -> 0;
            case "organization" -> 1;
            case "education" -> 2;
            default -> 3;
        };
    }

    private static String httpMethod(RequestMappingInfo info) {
        RequestMethodsRequestCondition methodsCondition = info.getMethodsCondition();
        return methodsCondition.getMethods().isEmpty()
            ? "ANY"
            : methodsCondition.getMethods().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private static String pathOf(RequestMappingInfo info) {
        PathPatternsRequestCondition pathPatterns = info.getPathPatternsCondition();
        return pathPatterns == null ? "" : String.join(",", pathPatterns.getPatternValues());
    }

    private record TableRef(String tableName, String mapperClass, String moduleKey) {
    }

    private record Deps(List<EndpointChainServiceView> services, List<TableRef> tables) {
    }
}
