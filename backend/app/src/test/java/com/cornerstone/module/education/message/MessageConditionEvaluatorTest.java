package com.cornerstone.module.education.message;

import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.message.support.MessageCondition;
import com.cornerstone.module.education.message.support.MessageConditionEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 白名单条件求值：口径是「配置错误必须报错」，不能静默放过或误命中。
 *
 * <p>这里的每个分支都直接决定事件会不会产生，所以数字比较走 BigDecimal，
 * 字符串比较按去空白后的字面值，列名大小写不敏感（JDBC 驱动的列名大小写不统一）。</p>
 */
class MessageConditionEvaluatorTest {

    private final MessageConditionEvaluator evaluator = new MessageConditionEvaluator(new ObjectMapper());

    @Test
    void emptyConfigMatchesEveryRow() {
        assertTrue(evaluator.matches(row("missing_count", 0), evaluator.parse(null)));
        assertTrue(evaluator.matches(row("missing_count", 0), evaluator.parse("  ")));
        assertTrue(evaluator.matches(row("missing_count", 0), evaluator.parse("[]")));
    }

    @Test
    void multipleConditionsAreJoinedWithAnd() {
        List<MessageCondition> conditions = evaluator.parse("""
            [{"column":"missing_count","op":"gt","value":0},
             {"column":"status","op":"eq","value":"pending"}]
            """);
        Map<String, Object> hit = row("missing_count", 2);
        hit.put("status", "pending");
        Map<String, Object> missed = row("missing_count", 2);
        missed.put("status", "done");

        assertTrue(evaluator.matches(hit, conditions));
        assertFalse(evaluator.matches(missed, conditions));
    }

    @Test
    void numericComparisonUsesDecimalNotStringOrdering() {
        // JDBC 的 DECIMAL 以 BigDecimal 返回，BIGINT 以 Long 返回，配置值是 JSON 数字或字符串
        assertTrue(evaluator.matches(row("unpaid", new BigDecimal("100.51")), conditions("unpaid", "gt", "100.5")));
        assertTrue(evaluator.matches(row("unpaid", 10L), conditions("unpaid", "eq", 10)));
        assertFalse(evaluator.matches(row("unpaid", new BigDecimal("9.99")), conditions("unpaid", "gte", 10)));
        assertTrue(evaluator.matches(row("unpaid", "12.00"), conditions("unpaid", "lte", 12)));
        // 数值相等按数值算，不按 "10" 与 "10.0" 的字面差异算
        assertTrue(evaluator.matches(row("unpaid", 10), conditions("unpaid", "eq", "10.000")));
    }

    @Test
    void stringOpsTrimAndTreatNullAsEmpty() {
        Map<String, Object> row = row("remark", " 欠费 提醒 ");
        assertTrue(evaluator.matches(row, conditions("remark", "contains", "欠费")));
        assertFalse(evaluator.matches(row, conditions("remark", "contains", "退费")));
        assertTrue(evaluator.matches(rowName("remark"), conditions("remark", "eq", "")));
        assertTrue(evaluator.matches(row("level_code", "warning"), conditions("level_code", "ne", "critical")));
    }

    @Test
    void inAcceptsCommaStringAndJsonArray() {
        Map<String, Object> row = row("status", "active");
        assertTrue(evaluator.matches(row, conditions("status", "in", "active,pending")));
        assertTrue(evaluator.matches(row, conditions("status", "in", List.of("pending", "active"))));
        assertFalse(evaluator.matches(row, conditions("status", "in", "done,closed")));
        // 数字列用 in 时按字面值匹配
        assertTrue(evaluator.matches(row("missing_count", 3), conditions("missing_count", "in", "2,3")));
    }

    @Test
    void columnLookupIsCaseInsensitive() {
        assertTrue(evaluator.matches(row("Missing_Count", 3), conditions("missing_count", "gte", 3)));
    }

    @Test
    void invalidConfigFailsLoudly() {
        // 列名只允许标识符，杜绝把 SQL 片段塞进条件
        assertThrows(BusinessException.class, () -> evaluator.parse(
            "[{\"column\":\"1=1 OR x\",\"op\":\"eq\",\"value\":1}]"));
        assertThrows(BusinessException.class, () -> evaluator.parse(
            "[{\"column\":\"\",\"op\":\"eq\",\"value\":1}]"));
        // 不支持的比较符
        assertThrows(BusinessException.class, () -> evaluator.parse(
            "[{\"column\":\"a\",\"op\":\"like\",\"value\":\"x\"}]"));
        // 大小比较的配置值必须是数字，否则属于配置错误而不是「不命中」
        assertThrows(BusinessException.class, () -> evaluator.parse(
            "[{\"column\":\"a\",\"op\":\"gt\",\"value\":\"很多\"}]"));
        // 非 JSON
        assertThrows(BusinessException.class, () -> evaluator.parse("missing_count > 0"));
    }

    @Test
    void missingColumnThrowsInsteadOfSilentlyNotMatching() {
        // 视图列契约变了就应当让巡检报错、写 last_error，而不是悄悄一条事件都不出
        assertThrows(BusinessException.class,
            () -> evaluator.matches(row("a", 1), conditions("not_exists", "eq", 1)));
        // 非数字的运行时值做大小比较同样是错误
        assertThrows(BusinessException.class,
            () -> evaluator.matches(row("name", "abc"), conditions("name", "gt", 1)));
    }

    @Test
    void parseFillsConditionRecord() {
        List<MessageCondition> conditions = evaluator.parse(
            "[{\"column\":\"missing_count\",\"op\":\"GT\",\"value\":1}]");
        assertEquals(1, conditions.size());
        assertEquals("missing_count", conditions.get(0).column());
        assertEquals("GT", conditions.get(0).op());
        // 求值时不区分比较符大小写
        assertTrue(evaluator.matches(row("missing_count", 2), conditions));
    }

    private static List<MessageCondition> conditions(String column, String op, Object value) {
        return List.of(new MessageCondition(column, op, value));
    }

    private static Map<String, Object> row(String column, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(column, value);
        return row;
    }

    private static Map<String, Object> rowName(String column) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(column, null);
        return row;
    }
}
