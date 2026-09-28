package com.cornerstone.core.system;

import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.code.CodePattern;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 编号格式纯函数单测：把"号到底怎么长出来"钉死在这里，不连库。
 */
class CodePatternTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 28);

    @ParameterizedTest
    @MethodSource("segments")
    void segmentJoinsPrefixDateAndSeparator(String prefix, String separator, String datePattern, String expected) {
        assertEquals(expected, CodePattern.segment(prefix, separator, datePattern, DAY));
    }

    static Stream<Arguments> segments() {
        return Stream.of(
            // 带日期段:日期粒度即序号复位粒度
            Arguments.of("STU", "-", "yyyyMMdd", "STU-20260928-"),
            Arguments.of("STU", "-", "yyyy", "STU-2026-"),
            Arguments.of("STU", "-", "", "STU-"),
            // 无分隔符:各段直接相连
            Arguments.of("STU", "", "yyyyMMdd", "STU20260928"),
            Arguments.of("STU", "", "", "STU"),
            Arguments.of("STU", ".", "yyyyMM", "STU.202609."));
    }

    @ParameterizedTest
    @MethodSource("lastCodes")
    void sequenceAfterReadsTrailingDigits(String lastCode, long expected) {
        assertEquals(expected, CodePattern.sequenceAfter(lastCode));
    }

    static Stream<Arguments> lastCodes() {
        return Stream.of(
            Arguments.of("STU-20260928-007", 7L),
            Arguments.of("STU0012", 12L),
            // 手填的异形号没有可续的尾巴:从 1 起,占用情况交给取号侧的让号循环处理
            Arguments.of("STU-20260928-A12B", 0L),
            Arguments.of("STU-20260928-", 0L),
            Arguments.of(null, 0L),
            // 超出 long 的位数不是本生成器管的形态,同样退回 0
            Arguments.of("STU-999999999999999999999999", 0L));
    }

    @Test
    void formatPadsToSerialLengthAndWidensInsteadOfTruncating() {
        assertEquals("STU-20260928-001", CodePattern.format("STU-20260928-", 1, 3));
        assertEquals("STU-20260928-099", CodePattern.format("STU-20260928-", 99, 3));
        // 位数放不下时加宽:截断会直接造出重复号
        assertEquals("STU-20260928-100", CodePattern.format("STU-20260928-", 100, 3));
        assertEquals("STU-20260928-12345", CodePattern.format("STU-20260928-", 12345, 3));
    }

    @Test
    void likePatternEscapesWildcardsAndKeepsTrailingPercent() {
        assertEquals("STU-20260928-%", CodePattern.likePrefix("STU-20260928-"));
        // 只有 LIKE 的三个特殊字符加转义符，中划线原样通过
        assertEquals("STU\\%123\\_45\\\\%", CodePattern.likePrefix("STU%123_45\\"));
    }

    @Test
    void identifierMustBePlainLowercaseName() {
        CodePattern.requireSqlIdentifier("sys_institution", "表名");
        CodePattern.requireSqlIdentifier("org_code", "列名");
        assertThrows(BusinessException.class, () -> CodePattern.requireSqlIdentifier("sys_institution; DROP TABLE", "表名"));
        assertThrows(BusinessException.class, () -> CodePattern.requireSqlIdentifier("SysInstitution", "表名"));
        assertThrows(BusinessException.class, () -> CodePattern.requireSqlIdentifier("1_institution", "表名"));
        assertThrows(BusinessException.class, () -> CodePattern.requireSqlIdentifier(null, "表名"));
    }

    @Test
    void formatFieldsRejectValuesThatCouldBreakThePattern() {
        CodePattern.requirePrefix("STU");
        CodePattern.requirePrefix("edu_2026");
        // 通配符与空白会把 LIKE 段前缀扫歪,超长会让号超出列宽
        assertThrows(BusinessException.class, () -> CodePattern.requirePrefix("ST%U"));
        assertThrows(BusinessException.class, () -> CodePattern.requirePrefix("ST U"));
        assertThrows(BusinessException.class, () -> CodePattern.requirePrefix(""));

        CodePattern.requireSeparator("-");
        CodePattern.requireSeparator("");
        assertThrows(BusinessException.class, () -> CodePattern.requireSeparator("--"));
        assertThrows(BusinessException.class, () -> CodePattern.requireSeparator("/"));

        CodePattern.requireDatePattern("yyyyMM");
        CodePattern.requireDatePattern("");
        // 带 HH/mm/ss 的粒度会让"按日重排"变成"按秒重排",不在允许范围内
        assertThrows(BusinessException.class, () -> CodePattern.requireDatePattern("yyyyMMddHHmmss"));

        CodePattern.requireSerialLength(3);
        assertThrows(BusinessException.class, () -> CodePattern.requireSerialLength(0));
        assertThrows(BusinessException.class, () -> CodePattern.requireSerialLength(11));
    }
}
