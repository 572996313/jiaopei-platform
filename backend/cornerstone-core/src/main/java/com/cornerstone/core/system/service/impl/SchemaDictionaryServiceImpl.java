package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.config.MybatisPlusConfig;
import com.cornerstone.core.system.dto.SchemaColumnView;
import com.cornerstone.core.system.dto.SchemaDictionaryView;
import com.cornerstone.core.system.dto.SchemaTableView;
import com.cornerstone.core.system.schema.SchemaMaintenanceContributor;
import com.cornerstone.core.system.schema.SchemaMaintenanceEntry;
import com.cornerstone.core.system.service.SchemaDictionaryService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

/**
 * 数据字典实现：库结构读 INFORMATION_SCHEMA，实体注解靠运行时扫描补齐。
 *
 * <p>两条约束决定实现形态：</p>
 * <ol>
 *   <li><b>只读、零新表</b>：不引入任何手工维护的字典表，内容全部来自运行时事实，
 *       库结构一变页面自动跟着变，不存在"文档过期"。</li>
 *   <li><b>底座不认识业务</b>：模块归属由实体包名推导，维护入口由 {@link SchemaMaintenanceContributor}
 *       登记，本类不 import 任何业务包，ArchUnit 的底座边界规则照常成立。</li>
 * </ol>
 */
@Service
public class SchemaDictionaryServiceImpl implements SchemaDictionaryService {

    private static final Logger log = LoggerFactory.getLogger(SchemaDictionaryServiceImpl.class);

    /** 表结构不会每秒变；60 秒足够让新加的列"立刻"可见，又不至于每次刷新都扫元数据 */
    private static final long CACHE_TTL_MILLIS = 60_000L;

    private static final String CORE_MODULE = "core";
    private static final String OTHER_MODULE = "other";

    /** 排除 Flyway 自己的元数据表：它没有业务意义，字段也没法补注释，混进来会把"缺注释"统计带偏 */
    private static final String TABLES_SQL = """
        SELECT TABLE_NAME, TABLE_COMMENT
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME NOT LIKE 'flyway\\_%'
        ORDER BY TABLE_NAME
        """;

    private static final String COLUMNS_SQL = """
        SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
               COLUMN_COMMENT, COLUMN_KEY, EXTRA
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME NOT LIKE 'flyway\\_%'
        ORDER BY TABLE_NAME, ORDINAL_POSITION
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationContext applicationContext;
    private final List<SchemaMaintenanceContributor> contributors;

    /** 实体元数据与进程同生命周期：类路径运行期不会变，扫一次即可 */
    private volatile Map<String, EntityMeta> entityCache;
    private volatile SchemaDictionaryView viewCache;
    private volatile long viewCacheAt;

    public SchemaDictionaryServiceImpl(
        @Qualifier(MybatisPlusConfig.PRIMARY_DS) DataSource primaryDataSource,
        ApplicationContext applicationContext,
        List<SchemaMaintenanceContributor> contributors) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
        this.applicationContext = applicationContext;
        this.contributors = contributors;
    }

    @Override
    public SchemaDictionaryView dictionary() {
        long now = System.currentTimeMillis();
        SchemaDictionaryView cached = viewCache;
        if (cached != null && now - viewCacheAt < CACHE_TTL_MILLIS) {
            return cached;
        }
        synchronized (this) {
            if (viewCache != null && System.currentTimeMillis() - viewCacheAt < CACHE_TTL_MILLIS) {
                return viewCache;
            }
            SchemaDictionaryView fresh = load();
            viewCache = fresh;
            viewCacheAt = System.currentTimeMillis();
            return fresh;
        }
    }

    private SchemaDictionaryView load() {
        Map<String, EntityMeta> entities = entityMetadata();
        Map<String, SchemaMaintenanceEntry> maintenance = maintenanceIndex();
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

        Map<String, String> tableComments = new LinkedHashMap<>();
        jdbcTemplate.query(TABLES_SQL, (RowCallbackHandler) rs ->
            tableComments.put(rs.getString("TABLE_NAME"), nullToEmpty(rs.getString("TABLE_COMMENT"))));

        Map<String, List<SchemaColumnView>> columnsByTable = new LinkedHashMap<>();
        jdbcTemplate.query(COLUMNS_SQL, (RowCallbackHandler) rs -> {
            String tableName = rs.getString("TABLE_NAME");
            columnsByTable.computeIfAbsent(tableName, key -> new ArrayList<>()).add(
                toColumnView(rs.getString("COLUMN_NAME"), rs.getString("COLUMN_TYPE"),
                    rs.getString("IS_NULLABLE"), rs.getString("COLUMN_DEFAULT"),
                    rs.getString("COLUMN_COMMENT"), rs.getString("COLUMN_KEY"),
                    rs.getString("EXTRA"), entities.get(tableName)));
        });

        List<SchemaTableView> tables = new ArrayList<>();
        int columnCount = 0;
        int missingCommentCount = 0;
        for (Map.Entry<String, List<SchemaColumnView>> entry : columnsByTable.entrySet()) {
            String tableName = entry.getKey();
            List<SchemaColumnView> columns = entry.getValue();
            columnCount += columns.size();
            missingCommentCount += (int) columns.stream()
                .filter(column -> isBlank(column.comment()))
                .count();
            EntityMeta entity = entities.get(tableName);
            tables.add(new SchemaTableView(
                tableName,
                tableComments.getOrDefault(tableName, ""),
                entity == null ? OTHER_MODULE : entity.moduleKey(),
                entity == null ? null : entity.className(),
                columns,
                maintenance.get(tableName)));
        }
        tables.sort(Comparator
            .comparing((SchemaTableView table) -> CORE_MODULE.equals(table.moduleKey()) ? 0 : 1)
            .thenComparing(SchemaTableView::moduleKey)
            .thenComparing(SchemaTableView::tableName));

        return new SchemaDictionaryView(
            database,
            OffsetDateTime.now().toString(),
            tables.size(),
            columnCount,
            missingCommentCount,
            tables);
    }

    private SchemaColumnView toColumnView(String columnName, String columnType, String nullable,
                                          String defaultValue, String comment, String columnKey,
                                          String extra, EntityMeta entity) {
        FieldMeta field = entity == null ? null : entity.fields().get(columnName);
        return new SchemaColumnView(
            columnName,
            columnType,
            "YES".equalsIgnoreCase(nullable),
            defaultValue,
            nullToEmpty(comment),
            "PRI".equalsIgnoreCase(columnKey),
            nullToEmpty(extra),
            field == null ? null : field.javaField(),
            field == null ? null : field.javaType(),
            field == null ? null : field.description(),
            field == null ? List.of() : field.allowableValues());
    }

    private Map<String, SchemaMaintenanceEntry> maintenanceIndex() {
        Map<String, SchemaMaintenanceEntry> index = new HashMap<>();
        for (SchemaMaintenanceContributor contributor : contributors) {
            try {
                for (SchemaMaintenanceEntry entry : contributor.entries()) {
                    index.putIfAbsent(entry.tableName(), entry);
                }
            } catch (RuntimeException ex) {
                log.warn("数据字典维护入口登记失败，已跳过 moduleKey={}", contributor.moduleKey(), ex);
            }
        }
        return index;
    }

    // ------------------------------------------------------------------ 实体扫描

    private Map<String, EntityMeta> entityMetadata() {
        Map<String, EntityMeta> cached = entityCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (entityCache == null) {
                entityCache = scanEntities();
            }
            return entityCache;
        }
    }

    /**
     * 扫主类所在基准包下的 {@code @TableName} 实体。
     *
     * <p>基准包取 Spring Boot 的自动配置包（即启动类所在包），不写死 {@code com.cornerstone}——
     * 下游项目换成自己的包根时字典照样能用。</p>
     */
    Map<String, EntityMeta> scanEntities() {
        Map<String, EntityMeta> result = new HashMap<>();
        List<String> basePackages;
        try {
            basePackages = AutoConfigurationPackages.get(applicationContext);
        } catch (IllegalStateException ex) {
            log.warn("未取到自动配置基准包，数据字典的实体注解将为空", ex);
            return result;
        }
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidate : candidates) {
                String className = candidate.getBeanClassName();
                if (className == null) {
                    continue;
                }
                try {
                    Class<?> entityClass = Class.forName(className);
                    TableName tableName = entityClass.getAnnotation(TableName.class);
                    if (tableName == null || isBlank(tableName.value())) {
                        continue;
                    }
                    result.putIfAbsent(tableName.value(),
                        new EntityMeta(className, moduleKeyOf(className, basePackage), fieldsOf(entityClass)));
                } catch (ReflectiveOperationException | LinkageError ex) {
                    log.debug("跳过无法加载的实体 {}", className, ex);
                }
            }
        }
        return result;
    }

    /**
     * 模块归属由包名推导：{@code <基准包>.core.*} → core，{@code <基准包>.module.<x>.*} → x。
     * 这也解释了为什么底座不需要认识业务模块名——它只认包结构约定。
     */
    static String moduleKeyOf(String className, String basePackage) {
        String prefix = basePackage + ".";
        if (!className.startsWith(prefix)) {
            return OTHER_MODULE;
        }
        String relative = className.substring(prefix.length());
        String[] segments = relative.split("\\.");
        if (segments.length >= 2 && "module".equals(segments[0])) {
            return segments[1];
        }
        return segments.length == 0 ? OTHER_MODULE : segments[0];
    }

    private Map<String, FieldMeta> fieldsOf(Class<?> entityClass) {
        Map<String, FieldMeta> fields = new HashMap<>();
        for (Class<?> current = entityClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && !tableField.exist()) {
                    continue;
                }
                String columnName = tableField != null && !isBlank(tableField.value())
                    ? tableField.value()
                    : camelToUnderline(field.getName());
                Schema schema = field.getAnnotation(Schema.class);
                fields.putIfAbsent(columnName, new FieldMeta(
                    field.getName(),
                    field.getType().getSimpleName(),
                    schema == null ? null : schema.description(),
                    schema == null ? List.of() : List.of(schema.allowableValues())));
            }
        }
        return fields;
    }

    /** 驼峰转下划线，与 MyBatis-Plus 的 map-underscore-to-camel-case 互为逆运算 */
    static String camelToUnderline(String name) {
        StringBuilder builder = new StringBuilder(name.length() + 4);
        for (int index = 0; index < name.length(); index++) {
            char current = name.charAt(index);
            if (Character.isUpperCase(current)) {
                builder.append('_').append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    record FieldMeta(
        String javaField,
        String javaType,
        String description,
        List<String> allowableValues
    ) {
    }

    record EntityMeta(
        String className,
        String moduleKey,
        Map<String, FieldMeta> fields
    ) {
    }
}
