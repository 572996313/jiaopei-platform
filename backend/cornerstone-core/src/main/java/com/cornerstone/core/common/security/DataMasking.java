package com.cornerstone.core.common.security;

/**
 * 不可逆脱敏(打码)工具。规则:
 * <ul>
 *   <li>手机号:保留前 3 后 4,如 138****1234;</li>
 *   <li>身份证:保留前 3 后 4,如 110***********1234;</li>
 *   <li>银行卡:保留前 4 后 4,中间按 4 位一组分隔打码;</li>
 *   <li>密码:全量打码;</li>
 *   <li>邮箱:保留首字符与 @ 后域名,如 a***@example.com;</li>
 *   <li>姓名:仅保留首字符,如 张*;</li>
 *   <li>地址:保留前 6 个字符,其余打码;</li>
 *   <li>其他(OTHER):首尾各保留 1/4(向上取整),中间打码。</li>
 * </ul>
 */
public final class DataMasking {

    private static final char MASK_CHAR = '*';
    private static final int MAX_MASK_KEEP = 200;

    private DataMasking() {
    }

    /** 按类型打码;OTHER 且无长度信息时全量打码。 */
    public static String mask(String value, SensitiveType type) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return switch (type) {
            case PHONE -> phone(trimmed);
            case ID_CARD -> idCard(trimmed);
            case BANK_CARD -> bankCard(trimmed);
            case PASSWORD -> maskAll(trimmed);
            case EMAIL -> email(trimmed);
            case NAME -> name(trimmed);
            case ADDRESS -> keepHeadTail(trimmed, 6, 0);
            case OTHER -> keepHeadTail(trimmed, firstKeep(trimmed), lastKeep(trimmed));
        };
    }

    /** 手机号:138****1234。非 11 位纯数字时退化为首尾各保留一部分。 */
    public static String phone(String value) {
        String s = canon(value);
        if (s.length() != 11 || !s.chars().allMatch(Character::isDigit)) {
            return keepHeadTail(s, 3, 4);
        }
        return s.substring(0, 3) + mask(4) + s.substring(7);
    }

    /** 身份证:保留前 3 后 4,如 110***********1234。 */
    public static String idCard(String value) {
        return keepHeadTail(canon(value), 3, 4);
    }

    /** 银行卡:保留前 4 后 4,如 6222 **** **** 1234(按 4 位一组的分隔样式)。 */
    public static String bankCard(String value) {
        String s = canon(value).replaceAll("\\s+", "");
        if (s.length() <= 8) {
            return maskAll(s);
        }
        String head = s.substring(0, 4);
        String tail = s.substring(s.length() - 4);
        int middle = s.length() - 8;
        return head + " " + mask(middle) + " " + tail;
    }

    /** 密码:全量打码,固定 6 个星。 */
    public static String maskAll(String value) {
        if (value == null) {
            return null;
        }
        return "******";
    }

    /** 邮箱:保留首字符与 @ 后域名,如 a***@example.com。 */
    public static String email(String value) {
        String s = canon(value);
        int at = s.indexOf('@');
        if (at <= 0) {
            return keepHeadTail(s, 1, 0);
        }
        return s.charAt(0) + mask(3) + s.substring(at);
    }

    /** 姓名:仅保留首字符,如 张*。 */
    public static String name(String value) {
        String s = canon(value);
        int codePoints = s.codePointCount(0, s.length());
        if (codePoints <= 1) {
            return maskAll(s);
        }
        int firstEnd = s.offsetByCodePoints(0, 1);
        return s.substring(0, firstEnd) + MASK_CHAR;
    }

    /** 首尾各保留 head/tail 个字符,中间以连续星号打码。 */
    public static String keepHeadTail(String value, int head, int tail) {
        String s = canon(value);
        int codePoints = s.codePointCount(0, s.length());
        if (codePoints == 0) {
            return s;
        }
        head = Math.min(Math.max(head, 0), codePoints);
        tail = Math.min(Math.max(tail, 0), Math.max(0, codePoints - head));
        StringBuilder sb = new StringBuilder();
        if (head > 0) {
            sb.append(s, 0, s.offsetByCodePoints(0, head));
        }
        int middle = codePoints - head - tail;
        if (middle > 0) {
            sb.append(mask(middle));
        }
        if (tail > 0) {
            sb.append(s, s.offsetByCodePoints(0, codePoints - tail), s.length());
        }
        if (sb.length() == 0) {
            sb.append(MASK_CHAR);
        }
        return sb.toString();
    }

    private static int firstKeep(String s) {
        return Math.max(1, (int) Math.ceil(s.codePointCount(0, s.length()) / 4.0));
    }

    private static int lastKeep(String s) {
        return Math.max(1, (int) Math.ceil(s.codePointCount(0, s.length()) / 4.0));
    }

    private static String canon(String value) {
        return value == null ? "" : value.trim();
    }

    private static String mask(int count) {
        int n = Math.min(Math.max(count, 1), MAX_MASK_KEEP);
        return String.valueOf(MASK_CHAR).repeat(n);
    }
}