package com.cornerstone.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.system.schema.CoreSchemaMaintenanceContributor;
import com.cornerstone.core.system.schema.SchemaMaintenanceContributor;
import com.cornerstone.core.system.schema.SchemaMaintenanceEntry;
import com.cornerstone.module.education.config.EducationSchemaMaintenanceContributor;
import com.cornerstone.module.organization.config.OrganizationSchemaMaintenanceContributor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * 数据字典「维护入口」守护（纯反射检查，不起上下文、不连数据库）。
 *
 * <p>维护入口是手写的映射，写错表名不会有任何运行时报错——字典页只会少一个按钮，
 * 属于"看起来正常、实际失效"的那类问题。这里把三条口径钉死：</p>
 * <ol>
 *   <li>登记的表名必须真的存在（有对应 {@code @TableName} 实体）；</li>
 *   <li>入口路由必须以 {@code /} 开头，否则前端 router.push 会相对到当前页；</li>
 *   <li>同一张表不能被多个模块重复登记（底座与业务撞车时先登记者生效，隐式行为要避免）。</li>
 * </ol>
 */
class SchemaMaintenanceGuardTest {

    private static final List<SchemaMaintenanceContributor> CONTRIBUTORS = List.of(
        new CoreSchemaMaintenanceContributor(),
        new OrganizationSchemaMaintenanceContributor(),
        new EducationSchemaMaintenanceContributor());

    @Test
    void everyRegisteredTableMustExist() {
        Set<String> tables = entityTables();
        List<String> unknown = new ArrayList<>();
        for (SchemaMaintenanceContributor contributor : CONTRIBUTORS) {
            for (SchemaMaintenanceEntry entry : contributor.entries()) {
                if (!tables.contains(entry.tableName())) {
                    unknown.add(contributor.moduleKey() + " → " + entry.tableName());
                }
            }
        }
        assertThat(unknown).as("维护入口登记了不存在的表（检查表名拼写）").isEmpty();
    }

    @Test
    void everyEntryMustCarryLabelAndAbsolutePath() {
        List<String> violations = new ArrayList<>();
        for (SchemaMaintenanceContributor contributor : CONTRIBUTORS) {
            for (SchemaMaintenanceEntry entry : contributor.entries()) {
                if (entry.label() == null || entry.label().isBlank()) {
                    violations.add(entry.tableName() + " 缺中文入口名");
                }
                if (entry.path() != null && !entry.path().startsWith("/")) {
                    violations.add(entry.tableName() + " 的路由不是绝对路径：" + entry.path());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void sameTableMustNotBeRegisteredTwice() {
        Map<String, String> owners = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();
        for (SchemaMaintenanceContributor contributor : CONTRIBUTORS) {
            for (SchemaMaintenanceEntry entry : contributor.entries()) {
                String previous = owners.putIfAbsent(entry.tableName(), contributor.moduleKey());
                if (previous != null) {
                    conflicts.add(entry.tableName() + " 同时登记于 " + previous + " 与 " + contributor.moduleKey());
                }
            }
        }
        assertThat(conflicts).isEmpty();
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
}
