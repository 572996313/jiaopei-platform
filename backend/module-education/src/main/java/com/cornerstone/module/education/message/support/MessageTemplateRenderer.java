package com.cornerstone.module.education.message.support;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 事件文案/去重键/深链模板渲染：只做 {@code ${列名}} 纯文本替换。
 *
 * <p>模板在「事件类型保存」和「巡检执行」两个时点都会拿视图真实列校验，
 * 写错列名会直接报错并落到 last_error，不会静默渲染成空串。
 * 正文是纯文本快照，不存 HTML，前端负责转义展示。</p>
 */
@Component
public class MessageTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public String render(String template, Map<String, Object> row) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String column = matcher.group(1);
            matcher.appendReplacement(out, Matcher.quoteReplacement(format(value(row, column))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** 模板引用的列名集合，用于保存/执行前校验。 */
    public Set<String> referencedColumns(String template) {
        Set<String> columns = new LinkedHashSet<>();
        if (template == null || template.isEmpty()) {
            return columns;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            columns.add(matcher.group(1));
        }
        return columns;
    }

    /** 校验模板只引用真实列；写错列名按配置错误处理，不静默吞掉。 */
    public void validateColumns(String template, Set<String> availableColumns, String fieldLabel) {
        for (String column : referencedColumns(template)) {
            if (!containsIgnoreCase(availableColumns, column)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    fieldLabel + "引用了视图中不存在的列：" + column
                        + "；可用列：" + String.join("、", availableColumns));
            }
        }
    }

    private Object value(Map<String, Object> row, String column) {
        if (row.containsKey(column)) {
            return row.get(column);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(column)) {
                return entry.getValue();
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST,
            "事件模板引用了本轮视图中不存在的列：" + column);
    }

    private boolean containsIgnoreCase(Set<String> columns, String target) {
        return columns.stream().anyMatch(column -> column.equalsIgnoreCase(target));
    }

    private String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DATE_TIME);
        }
        if (value instanceof Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Time time) {
            return time.toLocalTime().format(TIME);
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME);
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalTime time) {
            return time.format(TIME);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }
}
