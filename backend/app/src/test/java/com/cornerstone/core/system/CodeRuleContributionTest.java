package com.cornerstone.core.system;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.system.code.CodePattern;
import com.cornerstone.core.system.code.CodeRuleContributor;
import com.cornerstone.core.system.code.CodeRuleRegistry;
import com.cornerstone.core.system.code.InstitutionCodeRuleContributor;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.module.education.config.EducationCodeRuleContributor;
import com.cornerstone.module.education.domain.entity.ClassTypeEntity;
import com.cornerstone.module.education.domain.entity.CourseCategoryEntity;
import com.cornerstone.module.education.domain.entity.CourseEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.organization.config.OrganizationCodeRuleContributor;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 各模块编号登记的守护：登记表里的表名列名列，必须是本仓库真在写编码的那张表那一列。
 *
 * <p>为什么单独守这一条：{@code CodeSequenceMapper} 是按登记进来的表名/列名<b>拼</b> SQL 的，
 * 拼错一张表名或一列名，取号不会报错——LIKE 扫不到任何东西，于是每次都回 001，
 * 存不存得进去再看运气。这种"静默取到错号"比启动失败难查得多，所以在合入期就用反射钉住：
 * 每张带编号的业务表都要有 {@code @TableName}，列名要能由实体上某个 {@code String} 字段按
 * MyBatis-Plus 的驼峰下划线规则推导出来。</p>
 *
 * <p>另一条：新增一个带编号的主数据页，登记要跟着补，否则那颗"生成"按钮报
 * "未登记的编码资源"。这条靠下面 {@code 每张带编号的业务表都登记了资源} 兜住——
 * 它扫的是实体清单，漏登记会直接红。</p>
 */
class CodeRuleContributionTest {

    /** 本仓库里"有编号列、且编号是人填的"那批表对应的实体。新增这类表要登记资源，也要进这张清单。 */
    private static final List<Class<?>> CODED_ENTITIES = List.of(
        InstitutionEntity.class,
        CampusEntity.class, ClassroomEntity.class, TermEntity.class, TimeSlotEntity.class,
        TeacherEntity.class, StudentEntity.class, TeachingClassEntity.class,
        CourseEntity.class, CourseCategoryEntity.class, ClassTypeEntity.class, FeeItemEntity.class);

    private static final Set<String> EXPECTED_RESOURCES = Set.of(
        "institution", "campus", "classroom", "term", "time_slot",
        "teacher", "student", "class", "course", "course_category", "class_type",
        "fee_item");

    private final CodeRuleRegistry registry = new CodeRuleRegistry(
        providersOf(new InstitutionCodeRuleContributor(),
            new OrganizationCodeRuleContributor(),
            new EducationCodeRuleContributor()));

    @Test
    void 三个模块登记的资源码就是这批() {
        assertEquals(EXPECTED_RESOURCES, registry.all().keySet());
    }

    @Test
    void 每张带编号的业务表都登记了资源() {
        Map<String, String> tableToResource = new HashMap<>();
        registry.all().forEach((resourceCode, spec) -> tableToResource.put(spec.tableName(), resourceCode));

        List<String> missing = new ArrayList<>();
        for (Class<?> entity : CODED_ENTITIES) {
            String table = tableNameOf(entity);
            assertNotNull(table, entity.getSimpleName() + " 上没有 @TableName");
            if (!tableToResource.containsKey(table)) {
                missing.add(entity.getSimpleName() + " -> " + table);
            }
        }
        assertTrue(missing.isEmpty(), "这些表有编号列但没登记资源，页面上的生成按钮会报未登记: " + missing);
    }

    @Test
    void 登记的列名必须是实体上真实存在的字符串字段() {
        Map<String, Class<?>> entitiesByTable = new HashMap<>();
        for (Class<?> entity : CODED_ENTITIES) {
            entitiesByTable.put(tableNameOf(entity), entity);
        }

        List<String> offenders = new ArrayList<>();
        registry.all().forEach((resourceCode, spec) -> {
            Class<?> entity = entitiesByTable.get(spec.tableName());
            if (entity == null) {
                offenders.add(resourceCode + " 指向一张不在清单里的表: " + spec.tableName());
                return;
            }
            boolean matched = declaredFields(entity).stream()
                .filter(field -> field.getType() == String.class)
                .anyMatch(field -> camelToSnake(field.getName()).equals(spec.columnName()));
            if (!matched) {
                offenders.add(resourceCode + " 的列 " + spec.tableName() + "." + spec.columnName()
                    + " 推不出 " + entity.getSimpleName() + " 上的任何 String 字段");
            }
        });

        assertTrue(offenders.isEmpty(), String.join(";", offenders));
    }

    @Test
    void 展示名带编号二字且资源码不带机构前缀() {
        List<String> offenders = new ArrayList<>();
        registry.all().forEach((resourceCode, spec) -> {
            String label = spec.label();
            // 报错文案是"XX不能为空"，展示名必须自己说清是哪种编号，否则操作员不知道填的是什么
            if (label == null || (!label.endsWith("编码") && !label.endsWith("编号")
                && !label.endsWith("工号") && !label.endsWith("学号"))) {
                offenders.add(resourceCode + " 的展示名 " + label);
            }
            if (resourceCode.startsWith("jp_") || resourceCode.startsWith("sys_")) {
                offenders.add(resourceCode + " 把表前缀带进了资源码");
            }
        });
        assertTrue(offenders.isEmpty(), String.join(";", offenders));
    }

    @Test
    void 默认格式拼出来的预览号不超过列宽() {
        // 六张表的编号列都是 varchar(64)，默认格式一上来就顶到上限的话，操作员加两位前缀就存不进去
        registry.all().values().forEach(spec -> {
            String preview = CodePattern.format(
                CodePattern.segment(spec.prefix(), spec.separator(), spec.datePattern(),
                    LocalDate.of(2026, 9, 28)),
                1L, spec.serialLength());
            assertTrue(preview.length() <= 32,
                spec.resourceCode() + " 的默认格式偏长: " + preview);
        });
    }

    /** 用一个真实的 ObjectProvider 而不是 mock：注册表走 Iterable 的默认方法 forEach，mock 会一条都收不到。 */
    private static ObjectProvider<CodeRuleContributor> providersOf(CodeRuleContributor... contributors) {
        List<CodeRuleContributor> items = List.of(contributors);
        return new ObjectProvider<>() {
            @Override
            public CodeRuleContributor getObject() {
                return items.isEmpty() ? null : items.get(0);
            }

            @Override
            public CodeRuleContributor getObject(Object... args) {
                return getObject();
            }

            @Override
            public CodeRuleContributor getIfAvailable() {
                return getObject();
            }

            @Override
            public CodeRuleContributor getIfUnique() {
                return getObject();
            }

            @Override
            public Iterator<CodeRuleContributor> iterator() {
                return items.iterator();
            }
        };
    }

    private static String tableNameOf(Class<?> entity) {
        TableName annotation = entity.getAnnotation(TableName.class);
        return annotation == null ? null : annotation.value();
    }

    private static List<Field> declaredFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(List.of(current.getDeclaredFields()));
        }
        return fields;
    }

    /** 与 MyBatis-Plus 默认的 map-underscore-to-camel-case 同口径的推导。 */
    private static String camelToSnake(String property) {
        StringBuilder sb = new StringBuilder(property.length() + 4);
        for (int i = 0; i < property.length(); i++) {
            char c = property.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
