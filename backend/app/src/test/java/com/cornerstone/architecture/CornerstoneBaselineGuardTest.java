package com.cornerstone.architecture;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.config.properties.CornerstoneProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 底座基线守护（纯资源/反射检查，不起上下文、不连数据库）。
 *
 * <p>阶段五-0e 把底座表结构收口成 core jar 自带的 {@code classpath:cornerstone/migration}
 * （业务库）与 {@code classpath:cornerstone/audit-migration}（日志库），供新项目直接启用；
 * 教培自己仍走 V7/V8/.../V45 的存量路径。"两条路径长出同一个库"由一次性人工结构比对定基线
 * （见 docs/底座下游接入说明.md），本测试拦住之后的漂移：
 * 基线脚本漏进 jar、唯一键漏了 deleted、字段漏了注释、表没钉排序规则、
 * 实体与基线表集合脱钩、教培误启用基线目录、种子夹带教培业务语义、
 * 迁移目录配置项退回 {@code @Value}（列表写法会静默失效）。</p>
 */
class CornerstoneBaselineGuardTest {

    private static final String SCHEMA_SCRIPT = "/cornerstone/migration/V100__cornerstone_core_schema.sql";
    private static final String INVITATION_SCRIPT = "/cornerstone/migration/V103__add_sys_auth_invitation.sql";
    private static final String SEED_SCRIPT = "/cornerstone/migration/V101__cornerstone_core_seed.sql";
    private static final String AUDIT_SCRIPT = "/cornerstone/audit-migration/V100__cornerstone_oper_log.sql";

    /** 基线结构脚本清单：V100 之后的底座增量表结构一律追加到这里，各守护自动覆盖。 */
    private static final List<String> SCHEMA_SCRIPTS = List.of(SCHEMA_SCRIPT, INVITATION_SCRIPT);

    @Test
    void flywayLocationsMustBindTheYamlListForm() {
        // 接入说明让下游用 YAML 列表写目录。这类属性在 Environment 里是 locations[0]/[1] 两个键，
        // @Value 的占位符取不到列表、只会回落到默认值（配了等于没配，且两条路径都能"看起来正常"），
        // 所以必须走 @ConfigurationProperties 的宽松绑定。改回 @Value 会让本测试红。
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("cornerstone.flyway.locations[0]", "classpath:db/migration");
        properties.put("cornerstone.flyway.locations[1]", "classpath:cornerstone/migration");
        properties.put("cornerstone.flyway.audit-locations[0]", "classpath:cornerstone/audit-migration");
        CornerstoneProperties bound = new Binder(new MapConfigurationPropertySource(properties))
            .bind("cornerstone", Bindable.of(CornerstoneProperties.class))
            .get();
        assertThat(bound.getFlyway().getLocations())
            .containsExactly("classpath:db/migration", "classpath:cornerstone/migration");
        assertThat(bound.getFlyway().getAuditLocations())
            .containsExactly("classpath:cornerstone/audit-migration");
    }

    @Test
    void baselineScriptsMustShipInsideCoreJar() {
        // 下游拿到的是 core 的 jar，不是源码：从 classpath 读得到才说明 resources 真的打进了产物。
        assertThat(read(SCHEMA_SCRIPT)).as("core jar 必须自带底座结构脚本").contains("CREATE TABLE");
        assertThat(read(INVITATION_SCRIPT)).as("core jar 必须自带注册凭证增量结构脚本").contains("CREATE TABLE");
        assertThat(read(SEED_SCRIPT)).as("core jar 必须自带底座种子脚本").contains("INSERT INTO");
        assertThat(read(AUDIT_SCRIPT)).as("core jar 必须自带日志库结构脚本").contains("CREATE TABLE");
    }

    @Test
    void everyBaselineUniqueKeyMustIncludeDeleted() {
        // @TableLogic 软删表的唯一键不含 deleted，软删一条后再建同名记录必撞 Duplicate entry。
        List<String> violations = new ArrayList<>();
        for (String script : SCHEMA_SCRIPTS) {
            forEachIndexLine(read(script), "UNIQUE KEY", (table, indexName, columns) -> {
                if (!columns.contains("deleted")) {
                    violations.add(table + "." + indexName + "(" + String.join(",", columns) + ")");
                }
            });
        }
        assertThat(violations).as("底座基线的唯一键必须一律含 deleted").isEmpty();
    }

    @Test
    void everyBaselineColumnMustHaveComment() {
        List<String> violations = new ArrayList<>();
        for (String script : SCHEMA_SCRIPTS) {
            forEachTable(read(script), (table, body) -> {
                for (String line : body) {
                    String trimmed = line.trim();
                    boolean isColumn = trimmed.startsWith("`");
                    boolean isConstraint = trimmed.toUpperCase().startsWith("PRIMARY KEY")
                        || trimmed.toUpperCase().startsWith("KEY ")
                        || trimmed.toUpperCase().startsWith("UNIQUE KEY")
                        || trimmed.toUpperCase().startsWith("CONSTRAINT");
                    if (isColumn && !isConstraint && !line.contains("COMMENT '")) {
                        violations.add(table + "." + trimmed.substring(1, trimmed.indexOf('`', 1)));
                    }
                }
            });
        }
        assertThat(violations).as("基线建表不允许出现无注释字段").isEmpty();
    }

    @Test
    void everyBaselineTableMustPinCollation() {
        // 只写 DEFAULT CHARSET=utf8mb4 会让排序规则随服务器默认漂移（MySQL 8.4 默认 utf8mb4_0900_ai_ci）。
        List<String> violations = new ArrayList<>();
        for (String script : SCHEMA_SCRIPTS) {
            violations.addAll(tablesMissingCollation(read(script)));
        }
        violations.addAll(tablesMissingCollation(read(AUDIT_SCRIPT)));
        assertThat(violations).as("基线每张表都要显式写 COLLATE=utf8mb4_unicode_ci").isEmpty();
    }

    @Test
    void baselineMustCoverExactlyTheCoreEntities() {
        // 底座加了新实体却漏进基线 => 新项目少表；基线留了没人用的表 => 新项目多表。两个方向都拦。
        Set<String> entities = coreEntityTables();
        Set<String> baselineTables = new TreeSet<>();
        for (String script : SCHEMA_SCRIPTS) {
            baselineTables.addAll(tableNames(read(script)));
        }
        baselineTables.addAll(tableNames(read(AUDIT_SCRIPT)));

        assertThat(entities).as("扫不到 core 实体说明包根或注解用法变了，本测试随之失效").isNotEmpty();
        assertThat(baselineTables).containsAll(entities);
        assertThat(entities).containsAll(baselineTables);
    }

    @Test
    void jiaopeiMustStayOnTheLegacyMigrationPath() {
        // 教培的 sys_ 表是 V7/V8/.../V45 演进来的。两套目录同时启用会重复建表，Flyway 直接启动失败。
        // 只有新项目才配 cornerstone/migration（接法见 docs/底座下游接入说明.md）。
        String yml = dataLines(read("/application.yml"));
        assertThat(yml)
            .as("教培不得启用 core jar 自带的基线目录")
            .doesNotContain("cornerstone/migration")
            .doesNotContain("cornerstone/audit-migration");
    }

    @Test
    void seedMustCarryNoJiaopeiBusinessSemantics() {
        // 基线种子只种底座自己的数据：业务权限码、各项目专属文案都不许进来。
        // 只看 INSERT 语句，注释里出现这些词是在解释"为什么没有"，不算违规。
        String seed = dataLines(read(SEED_SCRIPT));
        assertThat(seed)
            .as("种子不得含业务权限码前缀")
            .doesNotContain("organization:")
            .doesNotContain("education:");
        assertThat(seed)
            .as("登录页提示语属各项目自己的系统参数，不种进底座基线")
            .doesNotContain("auth.login.page_tip");
    }

    // ------------------------------------------------------------------ 解析工具

    interface IndexConsumer {
        void accept(String table, String indexName, List<String> columns);
    }

    interface TableConsumer {
        void accept(String table, List<String> bodyLines);
    }

    /** 遍历每张 CREATE TABLE：回调给出表名与其字段/约束行（含收尾的 ) ENGINE= 行）。 */
    private void forEachTable(String sql, TableConsumer consumer) {
        String table = null;
        List<String> body = new ArrayList<>();
        for (String raw : sql.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("--")) {
                continue;
            }
            if (line.startsWith("CREATE TABLE")) {
                table = between(line, "CREATE TABLE", "(");
                body = new ArrayList<>();
                continue;
            }
            if (table == null) {
                continue;
            }
            if (line.startsWith(") ENGINE=")) {
                body.add(line);
                consumer.accept(table, body);
                table = null;
                continue;
            }
            body.add(line);
        }
    }

    private void forEachIndexLine(String sql, String keyword, IndexConsumer consumer) {
        forEachTable(sql, (table, body) -> {
            for (String line : body) {
                if (!line.startsWith(keyword)) {
                    continue;
                }
                String name = line.substring(line.indexOf('`') + 1, line.indexOf('`', line.indexOf('`') + 1));
                int open = line.indexOf('(', line.indexOf('`', keyword.length()));
                int close = line.lastIndexOf(')');
                List<String> columns = new ArrayList<>();
                if (open >= 0 && close > open) {
                    for (String column : line.substring(open + 1, close).split(",")) {
                        columns.add(column.replace("`", "").trim());
                    }
                }
                consumer.accept(table, name, columns);
            }
        });
    }

    private List<String> tableNames(String sql) {
        List<String> names = new ArrayList<>();
        forEachTable(sql, (table, body) -> names.add(table));
        return names;
    }

    private List<String> tablesMissingCollation(String sql) {
        List<String> violations = new ArrayList<>();
        forEachTable(sql, (table, body) -> {
            if (!body.get(body.size() - 1).contains("COLLATE=utf8mb4_unicode_ci")) {
                violations.add(table);
            }
        });
        return violations;
    }

    private String between(String line, String left, String right) {
        int from = line.indexOf(left) + left.length();
        int to = line.indexOf(right, from);
        return line.substring(from, to).replace("`", "").trim();
    }

    /** 反射收集 core 各实体 @TableName 的表名（不启动 Spring 上下文，只做类路径扫描）。 */
    private Set<String> coreEntityTables() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        Set<String> tables = new TreeSet<>();
        for (var candidate : scanner.findCandidateComponents("com.cornerstone.core")) {
            if (!(candidate instanceof AnnotatedBeanDefinition)) {
                continue;
            }
            try {
                Class<?> type = Class.forName(candidate.getBeanClassName());
                TableName annotation = type.getAnnotation(TableName.class);
                if (annotation != null) {
                    tables.add(annotation.value());
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("基线守护扫不到实体:" + candidate.getBeanClassName(), e);
            }
        }
        return tables;
    }

    /**
     * 去掉整行注释（SQL 的 {@code --}、yml 的 {@code #}），只留生效内容。
     * 注释里出现某个目录/键名，是在解释"为什么没用它"，不该被"是否出现某串"的判定算作违规。
     */
    private String dataLines(String script) {
        StringBuilder sb = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--") && !trimmed.startsWith("#")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private String read(String classpathLocation) {
        try (InputStream in = getClass().getResourceAsStream(classpathLocation)) {
            assertThat(in).as("classpath 资源缺失:" + classpathLocation).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
