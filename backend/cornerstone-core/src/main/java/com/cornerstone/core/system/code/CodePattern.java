package com.cornerstone.core.system.code;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 编号的拼法与解析：前缀 + 日期段 + 序号，段与段之间用分隔符相连。
 *
 * <p>纯函数，不碰数据库，便于把"号到底怎么长出来"这件事单测干净。取号流程在
 * {@code CodeRuleServiceImpl}：按当前日期段找库里最大的同号 → 尾巴加一 → 撞上就继续加一。</p>
 */
public final class CodePattern {

    /** 允许的分隔符：空串表示各段直接相连。 */
    public static final Pattern SEPARATOR = Pattern.compile("^[-_.]?$");

    /** 允许的日期段格式。空串=不带日期段，此时序号全局累加；带日期段即按该粒度重排序号。 */
    public static final Set<String> DATE_PATTERNS = Set.of(
        "", "yyyyMMdd", "yyMMdd", "yyyyMM", "yyMM", "yyyy", "yy");

    /** 分隔符与日期段之外的前缀约束：标识符 charset，不放通配符、不放空白。 */
    public static final Pattern PREFIX = Pattern.compile("^[0-9A-Za-z_-]{1,16}$");

    /** 表名/列名/资源码的形态：只允许小写字母开头的标识符，用于拼进 SQL 前把住边界。 */
    public static final Pattern SQL_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public static final int MIN_SERIAL_LENGTH = 1;
    public static final int MAX_SERIAL_LENGTH = 10;

    private CodePattern() {
    }

    /**
     * 拼出"当前日期段前缀"，如 {@code STU-20260928-}。序号就在这一段之内累加，
     * 日期段换了（跨天/跨年、或不带日期段）自然从 1 重排。
     */
    public static String segment(String prefix, String separator, String datePattern, LocalDate date) {
        StringBuilder sb = new StringBuilder(prefix);
        if (!separator.isEmpty()) {
            sb.append(separator);
        }
        if (!datePattern.isEmpty()) {
            sb.append(DateTimeFormatter.ofPattern(datePattern).format(date));
            if (!separator.isEmpty()) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * 把同号段内已存在的最大编号解析成序号：取结尾那一串连续数字，没有结尾数字则视为 0。
     *
     * <p>手填过的异形编号（{@code STU-2026-01A}）解析成 0，下一个候选是 001；它若已被占用，
     * 由取号侧的"撞上就继续加一"让开，不会生成重复号。</p>
     */
    public static long sequenceAfter(String lastCode) {
        if (lastCode == null || lastCode.isEmpty()) {
            return 0L;
        }
        int end = lastCode.length();
        int start = end;
        while (start > 0 && Character.isDigit(lastCode.charAt(start - 1))) {
            start--;
        }
        if (start == end) {
            return 0L;
        }
        try {
            return Long.parseLong(lastCode.substring(start, end));
        } catch (NumberFormatException exception) {
            // 位数超出 long 的编号不属于本生成器管理的形态,按"没有可续的号"处理
            return 0L;
        }
    }

    /** 拼最终编号：序号不足位数补零，超出位数自动加宽（绝不截断成重复号）。 */
    public static String format(String segment, long sequence, int serialLength) {
        String digits = Long.toString(sequence);
        return digits.length() >= serialLength
            ? segment + digits
            : segment + "0".repeat(serialLength - digits.length()) + digits;
    }

    /**
     * LIKE 模式里的值要转义：前缀虽已限定 charset，仍把 {@code \}、{@code %}、{@code _}
     * 三个特殊字符按 MySQL 默认转义符处理，避免操作员改出个通配符来把整表扫一遍。
     */
    public static String likePrefix(String segment) {
        StringBuilder sb = new StringBuilder(segment.length() + 8);
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }

    // ------------------------------------------------------------- 校验（写入侧与启动期共用）

    /** 资源码/表名/列名：这两条要拼进 SQL，形态不对直接拒绝，不给任何解释空间。 */
    public static void requireSqlIdentifier(String value, String description) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                description + "只能是小写字母开头、由小写字母/数字/下划线组成的标识符:" + value);
        }
    }

    public static void requirePrefix(String value) {
        if (value == null || !PREFIX.matcher(value).matches()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "编号前缀只能是 1-16 位字母、数字、下划线或中划线:" + value);
        }
    }

    public static void requireSeparator(String value) {
        if (value == null || !SEPARATOR.matcher(value).matches()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "分隔符只能是中划线、下划线、圆点或留空(留空表示各段直接相连),当前值:" + value);
        }
    }

    public static void requireDatePattern(String value) {
        if (value == null || !DATE_PATTERNS.contains(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "日期段格式不支持:" + value + "，可选值：无(不配日期段)、" + String.join("、", sortedDatePatterns()));
        }
    }

    public static void requireSerialLength(int value) {
        if (value < MIN_SERIAL_LENGTH || value > MAX_SERIAL_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "序号位数只能是 " + MIN_SERIAL_LENGTH + "-" + MAX_SERIAL_LENGTH + " 位,当前值:" + value);
        }
    }

    private static List<String> sortedDatePatterns() {
        return DATE_PATTERNS.stream().filter(item -> !item.isEmpty()).sorted().toList();
    }
}
