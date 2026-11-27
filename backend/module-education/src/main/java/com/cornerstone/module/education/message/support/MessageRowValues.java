package com.cornerstone.module.education.message.support;

import java.util.Map;

/**
 * 视图行的列取值助手。
 *
 * <p>JDBC 返回的列名大小写取决于视图别名，这里统一做一次大小写不敏感查找，
 * 避免换驱动/换别名后条件与锚点突然读不到。</p>
 */
public final class MessageRowValues {

    private MessageRowValues() {
    }

    public static Object get(Map<String, Object> row, String column) {
        if (row == null || column == null) {
            return null;
        }
        if (row.containsKey(column)) {
            return row.get(column);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(column)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static Long asLong(Map<String, Object> row, String column) {
        Object value = get(row, column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static String asString(Map<String, Object> row, String column) {
        Object value = get(row, column);
        return value == null ? null : String.valueOf(value);
    }
}
