package com.cornerstone.core.common.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 基于 JSON 树(Jackson {@link com.fasterxml.jackson.databind.JsonNode} 之外的
 * 普通 Map/List/String 结构)的敏感数据处理器,对反序列化出的请求参数做递归脱敏。
 *
 * <p>识别规则:</p>
 * <ol>
 *   <li>字段名关键词(强):按小写下划线规范化后匹配手机/证件/银行卡/密码/邮箱/
 *       人名/地址等关键词,命中即套用对应类型;</li>
 *   <li>值格式兜底(弱):字段名未命中时,按 11 位手机、18 位身份证、13~19 位卡号、
 *       邮箱格式自动识别,避免漏记。</li>
 * </ol>
 *
 * <p>策略通过 {@link SensitiveStrategy} 控制;操作日志默认使用 {@code MASK}。
 * 递归遍历 Map/List 时对嵌套容器不做二次匹配(容器本身就是敏感字段的情形
 * 由值格式兜底或字段名命中处理)。</p>
 */
@Component
public class JsonSensitiveProcessor {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{13,19}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private static final Map<String, SensitiveType> FIELD_RULES = new LinkedHashMap<>();

    static {
        // 手机 / 电话
        FIELD_RULES.put("phone", SensitiveType.PHONE);
        FIELD_RULES.put("mobile", SensitiveType.PHONE);
        FIELD_RULES.put("tel", SensitiveType.PHONE);
        FIELD_RULES.put("telephone", SensitiveType.PHONE);
        FIELD_RULES.put("contact_phone", SensitiveType.PHONE);
        FIELD_RULES.put("work_phone", SensitiveType.PHONE);
        FIELD_RULES.put("home_phone", SensitiveType.PHONE);
        // 证件号
        FIELD_RULES.put("id_card", SensitiveType.ID_CARD);
        FIELD_RULES.put("idcard", SensitiveType.ID_CARD);
        FIELD_RULES.put("id_no", SensitiveType.ID_CARD);
        FIELD_RULES.put("id_number", SensitiveType.ID_CARD);
        FIELD_RULES.put("citizen_no", SensitiveType.ID_CARD);
        FIELD_RULES.put("government_no", SensitiveType.ID_CARD);
        FIELD_RULES.put("social_no", SensitiveType.ID_CARD);
        // 银行卡 / 账号
        FIELD_RULES.put("bank_card", SensitiveType.BANK_CARD);
        FIELD_RULES.put("bankcard", SensitiveType.BANK_CARD);
        FIELD_RULES.put("card_no", SensitiveType.BANK_CARD);
        FIELD_RULES.put("cardno", SensitiveType.BANK_CARD);
        FIELD_RULES.put("bank_card_no", SensitiveType.BANK_CARD);
        FIELD_RULES.put("account_no", SensitiveType.BANK_CARD);
        FIELD_RULES.put("acct_no", SensitiveType.BANK_CARD);
        // 密码 / 密钥
        FIELD_RULES.put("password", SensitiveType.PASSWORD);
        FIELD_RULES.put("pwd", SensitiveType.PASSWORD);
        FIELD_RULES.put("passwd", SensitiveType.PASSWORD);
        FIELD_RULES.put("old_password", SensitiveType.PASSWORD);
        FIELD_RULES.put("new_password", SensitiveType.PASSWORD);
        FIELD_RULES.put("confirm_password", SensitiveType.PASSWORD);
        FIELD_RULES.put("initial_password", SensitiveType.PASSWORD);
        FIELD_RULES.put("reset_password", SensitiveType.PASSWORD);
        FIELD_RULES.put("secret", SensitiveType.PASSWORD);
        FIELD_RULES.put("client_secret", SensitiveType.PASSWORD);
        FIELD_RULES.put("app_secret", SensitiveType.PASSWORD);
        FIELD_RULES.put("access_key", SensitiveType.PASSWORD);
        // 邮箱
        FIELD_RULES.put("email", SensitiveType.EMAIL);
        FIELD_RULES.put("mail", SensitiveType.EMAIL);
        FIELD_RULES.put("email_address", SensitiveType.EMAIL);
        // 人名(仅明确的「人名」字段,避免误伤课程名 / 机构名 / 菜单名)
        FIELD_RULES.put("real_name", SensitiveType.NAME);
        FIELD_RULES.put("parent_name", SensitiveType.NAME);
        FIELD_RULES.put("student_name", SensitiveType.NAME);
        FIELD_RULES.put("teacher_name", SensitiveType.NAME);
        FIELD_RULES.put("contact_name", SensitiveType.NAME);
        FIELD_RULES.put("guardian_name", SensitiveType.NAME);
        FIELD_RULES.put("kid_name", SensitiveType.NAME);
        FIELD_RULES.put("legacy_name", SensitiveType.NAME);
        FIELD_RULES.put("head_name", SensitiveType.NAME);
        // 地址
        FIELD_RULES.put("address", SensitiveType.ADDRESS);
        FIELD_RULES.put("addr", SensitiveType.ADDRESS);
        FIELD_RULES.put("home_address", SensitiveType.ADDRESS);
        FIELD_RULES.put("reside_address", SensitiveType.ADDRESS);
        FIELD_RULES.put("detail_address", SensitiveType.ADDRESS);
    }

    private final SensitiveCipher sensitiveCipher;

    public JsonSensitiveProcessor(SensitiveCipher sensitiveCipher) {
        this.sensitiveCipher = sensitiveCipher;
    }

    /** 按策略深度处理整棵 JSON 树(Mask 策略下与 DeepMask 等价)。 */
    public Object process(Object node, SensitiveStrategy strategy) {
        if (node == null) {
            return null;
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                result.put(key, processValue(key, entry.getValue(), strategy));
            }
            return result;
        }
        if (node instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(process(item, strategy));
            }
            return result;
        }
        return node;
    }

    private Object processValue(String key, Object value, SensitiveStrategy strategy) {
        if (value instanceof Map || value instanceof List) {
            return process(value, strategy);
        }
        if (value instanceof String text) {
            Optional<SensitiveType> byName = matchByName(key);
            SensitiveType type = byName.orElseGet(() -> matchByValue(text));
            if (type == null) {
                return text;
            }
            return apply(text, type, strategy);
        }
        return value;
    }

    private String apply(String value, SensitiveType type, SensitiveStrategy strategy) {
        return switch (strategy) {
            case PLAIN -> value;
            case MASK -> DataMasking.mask(value, type);
            case ENCRYPT -> sensitiveCipher.encrypt(value);
            case ENCRYPT_MASK -> sensitiveCipher.encrypt(value);
        };
    }

    private Optional<SensitiveType> matchByName(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        // 标准化:小写 + 驼峰转下划线 + 空白/连字符归一,如 realName→real_name、contactPhone→contact_phone
        String normalized = key.trim()
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("[-\\s]", "_")
            .toLowerCase(Locale.ROOT);
        SensitiveType type = FIELD_RULES.get(normalized);
        return Optional.ofNullable(type);
    }

    private SensitiveType matchByValue(String value) {
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (PHONE_PATTERN.matcher(text).matches()) {
            return SensitiveType.PHONE;
        }
        if (ID_CARD_PATTERN.matcher(text).matches()) {
            return SensitiveType.ID_CARD;
        }
        if (BANK_CARD_PATTERN.matcher(text).matches() && !ID_CARD_PATTERN.matcher(text).matches()) {
            return SensitiveType.BANK_CARD;
        }
        if (EMAIL_PATTERN.matcher(text).matches()) {
            return SensitiveType.EMAIL;
        }
        return null;
    }
}