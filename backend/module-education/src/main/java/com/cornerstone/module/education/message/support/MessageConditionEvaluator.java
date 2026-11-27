package com.cornerstone.module.education.message.support;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 事件条件解析与求值。
 *
 * <p>只支持视图列上的白名单比较，不开任意 SQL/表达式输入口。条件为空表示命中全部候选行；
 * 多条件之间取 AND。缺列、非法比较符一律按配置错误抛出，由巡检把原因写回 last_error。</p>
 */
@Component
public class MessageConditionEvaluator {

    private static final Set<String> OPS = Set.of("eq", "ne", "gt", "gte", "lt", "lte", "contains", "in");
    private final ObjectMapper objectMapper;

    public MessageConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<MessageCondition> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<MessageCondition> conditions = objectMapper.readValue(
                json, new TypeReference<List<MessageCondition>>() {
                });
            for (MessageCondition condition : conditions) {
                validate(condition);
            }
            return conditions;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "事件条件 JSON 格式无效：" + exception.getMessage());
        }
    }

    public boolean matches(Map<String, Object> row, List<MessageCondition> conditions) {
        for (MessageCondition condition : conditions) {
            if (!matches(row, condition)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(Map<String, Object> row, MessageCondition condition) {
        Object actual = value(row, condition.column());
        String op = condition.op() == null ? "" : condition.op().trim().toLowerCase(Locale.ROOT);
        return switch (op) {
            case "eq" -> compareEquals(actual, condition.value());
            case "ne" -> !compareEquals(actual, condition.value());
            case "gt" -> compareNumber(actual, condition.value()) > 0;
            case "gte" -> compareNumber(actual, condition.value()) >= 0;
            case "lt" -> compareNumber(actual, condition.value()) < 0;
            case "lte" -> compareNumber(actual, condition.value()) <= 0;
            case "contains" -> asString(actual).contains(asString(condition.value()));
            case "in" -> asSet(condition.value()).contains(asString(actual));
            default -> throw new BusinessException(ResultCode.BAD_REQUEST,
                "不支持的事件条件比较符：" + condition.op());
        };
    }

    private void validate(MessageCondition condition) {
        if (condition == null || condition.column() == null
            || !condition.column().matches("[A-Za-z0-9_]+")) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "事件条件必须指定合法的视图列名（只允许字母、数字、下划线）");
        }
        String op = condition.op() == null ? "" : condition.op().trim().toLowerCase(Locale.ROOT);
        if (!OPS.contains(op)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "不支持的事件条件比较符：" + condition.op() + "；可用：" + String.join("、", OPS));
        }
        if ((op.equals("gt") || op.equals("gte") || op.equals("lt") || op.equals("lte"))
            && toDecimal(condition.value()) == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "事件条件 " + condition.column() + " 使用 " + op + " 时必须是数字");
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
            "事件条件引用了本轮视图中不存在的列：" + column);
    }

    private boolean compareEquals(Object actual, Object expected) {
        BigDecimal actualNumber = toDecimal(actual);
        BigDecimal expectedNumber = toDecimal(expected);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber) == 0;
        }
        return asString(actual).equals(asString(expected));
    }

    private int compareNumber(Object actual, Object expected) {
        BigDecimal left = toDecimal(actual);
        BigDecimal right = toDecimal(expected);
        if (left == null || right == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "事件条件比较需要数字，实际值：" + actual + " / " + expected);
        }
        return left.compareTo(right);
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Set<String> asSet(Object value) {
        Set<String> values = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                values.add(asString(item));
            }
            return values;
        }
        String text = asString(value);
        if (text.isEmpty()) {
            return values;
        }
        for (String item : text.split(",")) {
            values.add(item.trim());
        }
        return values;
    }

    /** 供类型保存时做一次结构校验，不依赖具体数据行。 */
    public List<MessageCondition> parseAndValidate(String json) {
        List<MessageCondition> conditions = new ArrayList<>(parse(json));
        return conditions;
    }
}
