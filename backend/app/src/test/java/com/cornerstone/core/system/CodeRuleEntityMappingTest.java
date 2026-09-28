package com.cornerstone.core.system;

import com.cornerstone.core.system.domain.entity.CodeRuleEntity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编号规则实体的列名映射守护。
 *
 * <p>踩过的坑：{@code sys_code_rule} 起初把分隔符列叫 {@code separator}，接口一上线就 1064。
 * MyBatis-Plus 给 wrapper 生成的 SQL <b>既不反引号列名、也不反引号别名</b>，所以列名和 Java 字段名
 * 两边只要有一边撞上 MySQL 保留字都会炸——只改列名不够（它会拼成 {@code separator_char AS separator}）。
 * 这张表是底座表，下游新项目从零建库也带同样的列名，改一次要两边同步，所以把判据钉在这里。</p>
 */
class CodeRuleEntityMappingTest {

    /**
     * 会撞的保留字取 MySQL 8.0 保留字里"当列名/别名最自然"的那批（不是全量清单，全量太长反而没人维护）。
     * 判定按整词比对，所以 date_pattern、serial_length 这类带下划线的名字不受影响。
     */
    private static final Set<String> RISKY_RESERVED = Set.of(
        "separator", "order", "group", "key", "desc", "asc", "limit", "offset", "match", "range",
        "usage", "call", "case", "when", "then", "else", "end", "if", "left", "right", "inner",
        "like", "is", "not", "and", "or", "xor", "to", "by", "from", "into", "select", "insert",
        "update", "delete", "set", "values", "default", "null", "primary", "unique", "index",
        "interval", "read", "write", "distinct", "as", "on", "in", "exists", "between");

    @Test
    void 字段名与推导出的列名都不许撞上保留字() {
        List<String> offenders = new ArrayList<>();
        for (Field field : declaredFields(CodeRuleEntity.class)) {
            String property = field.getName();
            String column = camelToSnake(property);
            if (RISKY_RESERVED.contains(property.toLowerCase())) {
                offenders.add("字段名 " + property);
            }
            if (RISKY_RESERVED.contains(column)) {
                offenders.add("列名 " + column);
            }
        }

        assertTrue(offenders.isEmpty(), String.join(";", offenders));
    }

    @Test
    void 分隔符按列名_separator_char_落库() {
        // 名字改过两轮，钉住"字段=separatorChar / 列=separator_char"这一对应，别再退回单数拼法
        assertTrue(declaredFields(CodeRuleEntity.class).stream()
            .anyMatch(field -> "separatorChar".equals(field.getName())));
        assertEquals("separator_char", camelToSnake("separatorChar"));
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
