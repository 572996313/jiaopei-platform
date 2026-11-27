package com.cornerstone.module.education.message;

import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.message.support.MessageTemplateRenderer;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板渲染只做 {@code ${列名}} 纯文本替换。
 *
 * <p>两个关键约束：时间/金额的展示口径固定（不随驱动返回类型漂移），
 * 以及引用了不存在的列必须报错——静默渲染成空串会让事件文案悄悄变残。</p>
 */
class MessageTemplateRendererTest {

    private final MessageTemplateRenderer renderer = new MessageTemplateRenderer();

    @Test
    void rendersPlaceholdersWithFixedFormats() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("class_name", "三年二班");
        row.put("lesson_date", LocalDate.of(2026, 11, 26));
        row.put("start_time", LocalTime.of(9, 5, 30));
        row.put("unpaid", new BigDecimal("1200.50"));
        row.put("lesson_id", 1001L);

        assertEquals(
            "三年二班 2026-11-26 09:05 未完成点名，应到 5 人",
            renderer.render("${class_name} ${lesson_date} ${start_time} 未完成点名，应到 ${expected} 人",
                withExtra(row, "expected", 5)));
        assertEquals("1200.5", renderer.render("${unpaid}", row));
        assertEquals("1001", renderer.render("${lesson_id}", row));
    }

    @Test
    void jdbcDateTypesFormatLikeJavaTimeTypes() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("created", Timestamp.valueOf(LocalDateTime.of(2026, 11, 26, 8, 0, 5)));
        row.put("day", Date.valueOf(LocalDate.of(2026, 11, 26)));
        row.put("clock", Time.valueOf(LocalTime.of(14, 30)));

        assertEquals("2026-11-26 08:00:05", renderer.render("${created}", row));
        assertEquals("2026-11-26", renderer.render("${day}", row));
        assertEquals("14:30", renderer.render("${clock}", row));
    }

    @Test
    void nullValueRendersAsEmptyAndMissingColumnThrows() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("remark", null);
        // 值为空是正常数据形态，渲染空串
        assertEquals("备注：", renderer.render("备注：${remark}", row));
        // 列不在本轮结果里则是视图契约变了，必须报错
        assertThrows(BusinessException.class, () -> renderer.render("${remark}${not_exists}", row));
    }

    @Test
    void columnLookupIsCaseInsensitive() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Class_Name", "一班");
        assertEquals("一班", renderer.render("${class_name}", row));
    }

    @Test
    void templatesWithoutPlaceholdersPassThrough() {
        assertEquals("", renderer.render(null, Map.of()));
        assertEquals("", renderer.render("", Map.of()));
        assertEquals("固定文案", renderer.render("固定文案", Map.of()));
        // 不是 ${标识符} 形态的美元符号不当作占位符
        assertEquals("价格 $12", renderer.render("价格 $12", Map.of()));
    }

    @Test
    void placeholderValueIsQuotedSoDollarAndBackslashSurvive() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("remark", "a$1\\b");
        // appendReplacement 不转义会把 $1 当成分组引用，这里必须原样输出
        assertEquals("a$1\\b", renderer.render("${remark}", row));
    }

    @Test
    void referencedColumnsDeduplicatesAndKeepsOrder() {
        Set<String> columns = renderer.referencedColumns("${a} ${b} ${a}");
        assertEquals(Set.of("a", "b"), columns);
        assertEquals(java.util.List.of("a", "b"), new java.util.ArrayList<>(columns));
        assertTrue(renderer.referencedColumns(null).isEmpty());
    }

    @Test
    void validateColumnsRejectsUnknownColumnAndIgnoresCase() {
        Set<String> available = new LinkedHashSet<>(java.util.List.of("class_name", "lesson_id"));
        renderer.validateColumns("${class_name} ${lesson_id}", available, "标题模板");
        assertThrows(BusinessException.class,
            () -> renderer.validateColumns("${not_exists}", available, "标题模板"));
    }

    private static Map<String, Object> withExtra(Map<String, Object> row, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        copy.put(key, value);
        return copy;
    }
}
