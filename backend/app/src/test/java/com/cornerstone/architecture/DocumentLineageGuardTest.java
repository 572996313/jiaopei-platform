package com.cornerstone.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.system.lineage.DocumentEdge;
import com.cornerstone.core.system.lineage.DocumentLineageContributor;
import com.cornerstone.core.system.lineage.DocumentNode;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务单据链路守护：声明必须自洽，且真的能落到库表和接口上。
 *
 * <p>声明式链路最大的风险是「写错了也没有运行时报错」：表名拼错、接口路径改了、
 * 单据编码两端对不上，页面只会少一条边。这里用纯反射 + 扫描把四件事钉死：</p>
 * <ol>
 *   <li>单据编码全局唯一，主表与附属表都有对应 {@code @TableName} 实体；</li>
 *   <li>关系的两端都已登记，关联字段、关系类型不为空；</li>
 *   <li>触发接口真实存在（方法 + 路径按路径变量归一后匹配）；</li>
 *   <li>标为「待确认」的节点/关系必须写清原因，不能只挂一个 false。</li>
 * </ol>
 */
class DocumentLineageGuardTest {

    private static final Pattern ENDPOINT_FORMAT =
        Pattern.compile("^(GET|POST|PUT|DELETE|PATCH) /.+$");
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^/}]+}");

    private static List<DocumentLineageContributor> contributors;
    private static Set<String> entityTables;
    private static Set<String> controllerEndpoints;

    @BeforeAll
    static void setUp() throws ReflectiveOperationException {
        contributors = loadContributors();
        entityTables = entityTables();
        controllerEndpoints = controllerEndpoints();
    }

    @Test
    void documentCodesMustBeUnique() {
        Map<String, String> owners = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();
        for (DocumentLineageContributor contributor : contributors) {
            for (DocumentNode node : nodesOf(contributor)) {
                String previous = owners.putIfAbsent(node.code(), contributor.moduleKey());
                if (previous != null) {
                    conflicts.add(node.code() + " 同时登记于 " + previous + " 与 " + contributor.moduleKey());
                }
            }
        }
        assertThat(conflicts).as("单据编码重复，页面会只显示一条").isEmpty();
    }

    @Test
    void documentsMustCarryIdentityAndExistingTables() {
        List<String> violations = new ArrayList<>();
        for (DocumentLineageContributor contributor : contributors) {
            for (DocumentNode node : nodesOf(contributor)) {
                if (isBlank(node.code())) {
                    violations.add(contributor.moduleKey() + "：单据编码为空");
                }
                if (isBlank(node.name())) {
                    violations.add(node.code() + "：缺少中文名");
                }
                if (isBlank(node.primaryTable())) {
                    violations.add(node.code() + "：缺少主表");
                } else if (!entityTables.contains(node.primaryTable())) {
                    violations.add(node.code() + "：主表 " + node.primaryTable() + " 没有对应实体");
                }
                if (node.relatedTables() != null) {
                    for (String table : node.relatedTables()) {
                        if (!entityTables.contains(table)) {
                            violations.add(node.code() + "：附属表 " + table + " 没有对应实体");
                        }
                    }
                }
                if (node.pagePath() != null && !node.pagePath().startsWith("/")) {
                    violations.add(node.code() + "：页面路由必须是绝对路径，实际为 " + node.pagePath());
                }
            }
        }
        assertThat(violations).as("单据节点字段不完整或表名拼错").isEmpty();
    }

    @Test
    void edgesMustReferenceDeclaredDocumentsAndDescribeThemselves() {
        Set<String> codes = new TreeSet<>();
        for (DocumentLineageContributor contributor : contributors) {
            for (DocumentNode node : nodesOf(contributor)) {
                codes.add(node.code());
            }
        }
        List<String> violations = new ArrayList<>();
        for (DocumentLineageContributor contributor : contributors) {
            for (DocumentEdge edge : edgesOf(contributor)) {
                if (!isKnownDocumentCode(edge.fromCode(), codes)) {
                    violations.add(contributor.moduleKey() + "：关系来源未登记 " + edge.fromCode());
                }
                if (!isKnownDocumentCode(edge.toCode(), codes)) {
                    violations.add(contributor.moduleKey() + "：关系目标未登记 " + edge.toCode());
                }
                if (isBlank(edge.relation())) {
                    violations.add(edge.fromCode() + " → " + edge.toCode() + "：缺少关系类型");
                }
                if (edge.linkFields() == null || edge.linkFields().isEmpty()
                    || edge.linkFields().stream().anyMatch(DocumentLineageGuardTest::isBlank)) {
                    violations.add(edge.fromCode() + " → " + edge.toCode() + "：关联字段为空");
                }
            }
        }
        assertThat(violations).as("单据关系不完整或两端未登记").isEmpty();
    }

    @Test
    void declaredEndpointsMustExist() {
        List<String> violations = new ArrayList<>();
        for (DocumentLineageContributor contributor : contributors) {
            for (DocumentEdge edge : edgesOf(contributor)) {
                String endpoint = edge.triggerEndpoint();
                if (endpoint == null) {
                    continue;
                }
                if (!ENDPOINT_FORMAT.matcher(endpoint).matches()) {
                    violations.add(endpoint + "：格式应为 METHOD /path");
                    continue;
                }
                String[] parts = endpoint.split(" ", 2);
                String key = parts[0] + " " + normalizePath(parts[1]);
                if (!controllerEndpoints.contains(key)) {
                    violations.add(endpoint + "：没有对应的控制器接口（归一后 " + key + "）");
                }
            }
        }
        assertThat(violations).as("关系里登记了不存在的接口（检查路径或 HTTP 方法）").isEmpty();
    }

    @Test
    void unconfirmedItemsMustExplainWhy() {
        List<String> violations = new ArrayList<>();
        for (DocumentLineageContributor contributor : contributors) {
            for (DocumentNode node : nodesOf(contributor)) {
                if (!node.confirmed() && isBlank(node.remark())) {
                    violations.add(node.code() + "：标为待确认但没有 remark 说明");
                }
            }
            for (DocumentEdge edge : edgesOf(contributor)) {
                if (!edge.confirmed() && isBlank(edge.description())) {
                    violations.add(edge.fromCode() + " → " + edge.toCode() + "：标为待确认但没有说明");
                }
            }
        }
        assertThat(violations).as("待确认项必须写清原因，避免只挂一个 false").isEmpty();
    }

    // ------------------------------------------------------------------ 扫描

    private static List<DocumentLineageContributor> loadContributors()
        throws ReflectiveOperationException {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(DocumentLineageContributor.class));
        List<DocumentLineageContributor> result = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.cornerstone")) {
            String className = definition.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> type = Class.forName(className);
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            // 链路登记应当只依赖静态数据；需要注入才能构造的实现不适合做 SPI。
            result.add((DocumentLineageContributor) type.getDeclaredConstructor().newInstance());
        }
        return result;
    }

    private static Set<String> entityTables() {
        Set<String> tables = new TreeSet<>();
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        for (BeanDefinition definition : scanner.findCandidateComponents("com.cornerstone")) {
            if (!(definition instanceof AnnotatedBeanDefinition annotated)) {
                continue;
            }
            Map<String, Object> attributes =
                annotated.getMetadata().getAnnotationAttributes(TableName.class.getName());
            Object value = attributes == null ? null : attributes.get("value");
            if (value instanceof String tableName && !tableName.isBlank()) {
                tables.add(tableName);
            }
        }
        return tables;
    }

    private static Set<String> controllerEndpoints() throws ReflectiveOperationException {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> endpoints = new TreeSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.cornerstone")) {
            String className = definition.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> controller = Class.forName(className);
            RequestMapping classMapping = AnnotationUtils.findAnnotation(controller, RequestMapping.class);
            String[] prefixes = classMapping == null ? new String[]{""} : mappingPaths(classMapping);
            for (Method method : controller.getDeclaredMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    Set<String> verbs = httpVerbs(annotation);
                    if (verbs.isEmpty()) {
                        continue;
                    }
                    for (String prefix : prefixes) {
                        for (String path : mappingPaths(annotation)) {
                            for (String verb : verbs) {
                                endpoints.add(verb + " " + normalizePath(joinPath(prefix, path)));
                            }
                        }
                    }
                }
            }
        }
        return endpoints;
    }

    private static Set<String> httpVerbs(Annotation annotation) {
        if (annotation instanceof GetMapping) {
            return Set.of("GET");
        }
        if (annotation instanceof PostMapping) {
            return Set.of("POST");
        }
        if (annotation instanceof PutMapping) {
            return Set.of("PUT");
        }
        if (annotation instanceof DeleteMapping) {
            return Set.of("DELETE");
        }
        if (annotation instanceof PatchMapping) {
            return Set.of("PATCH");
        }
        if (annotation instanceof RequestMapping mapping) {
            Set<String> verbs = new TreeSet<>();
            for (RequestMethod requestMethod : mapping.method()) {
                verbs.add(requestMethod.name());
            }
            return verbs;
        }
        return Set.of();
    }

    private static String[] mappingPaths(Annotation annotation) throws ReflectiveOperationException {
        String[] path = (String[]) annotation.annotationType().getMethod("path").invoke(annotation);
        if (path.length > 0) {
            return path;
        }
        String[] value = (String[]) annotation.annotationType().getMethod("value").invoke(annotation);
        return value.length > 0 ? value : new String[]{""};
    }

    private static String joinPath(String prefix, String path) {
        return "/" + prefix + "/" + path;
    }

    private static String normalizePath(String path) {
        String normalized = PATH_VARIABLE.matcher(path).replaceAll("{}");
        normalized = ("/" + normalized).replaceAll("/{2,}", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static List<DocumentNode> nodesOf(DocumentLineageContributor contributor) {
        return contributor.documents() == null ? List.of() : contributor.documents();
    }

    /** 关系可以指向显式登记的单据，也可以指向数据字典自动补的 {@code table:<表名>} 节点 */
    private static boolean isKnownDocumentCode(String code, Set<String> declaredCodes) {
        if (declaredCodes.contains(code)) {
            return true;
        }
        return code != null && code.startsWith("table:")
            && entityTables.contains(code.substring("table:".length()));
    }

    private static List<DocumentEdge> edgesOf(DocumentLineageContributor contributor) {
        return contributor.edges() == null ? List.of() : contributor.edges();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
