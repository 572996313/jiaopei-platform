package com.cornerstone.module.education;

import com.cornerstone.module.education.fee.support.FeeBillingEngine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 收费口径守护：公式与源表逐格核对过的样例全部钉在这里。
 *
 * <p>这些数字不是随便编的，来自 backend/退费明细.xlsx：保教费 1500/月、8 月半月 750、
 * 伙食费 18 天 × 30、王胤川实收多笔、未收费允许为负。改引擎前先让本用例保持绿。</p>
 */
class FeeBillingEngineTest {

    @Test
    void 应收等于月数乘月标准减上期结余减其他抵扣() {
        BigDecimal receivable = FeeBillingEngine.receivable(
            new BigDecimal("5.5"), new BigDecimal("1500"),
            new BigDecimal("750"), new BigDecimal("600"));
        // 5.5 × 1500 = 8250；8250 − 750 − 600 = 6900
        assertEquals(new BigDecimal("6900.00"), receivable);
    }

    @Test
    void 未收费为负表示预收而不是错误() {
        BigDecimal unpaid = FeeBillingEngine.unpaid(
            new BigDecimal("2250"), new BigDecimal("3750"));
        assertEquals(new BigDecimal("-1500.00"), unpaid);
    }

    @Test
    void 按月项目八月的半月发生额是七百五() {
        BigDecimal occurrence = FeeBillingEngine.monthlyOccurrence(
            new BigDecimal("1500"), new BigDecimal("0.50"), new BigDecimal("15"));
        assertEquals(new BigDecimal("750.00"), occurrence);
    }

    @Test
    void 按月项目当月无出勤默认不消耗学费() {
        BigDecimal occurrence = FeeBillingEngine.monthlyOccurrence(
            new BigDecimal("1500"), new BigDecimal("1.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.00"), occurrence);
    }

    @Test
    void 按天项目发生额等于出勤天数乘日标准() {
        BigDecimal occurrence = FeeBillingEngine.dailyOccurrence(
            new BigDecimal("30"), new BigDecimal("18"));
        assertEquals(new BigDecimal("540.00"), occurrence);
    }

    @Test
    void 结余等于实收减合计发生() {
        BigDecimal balance = FeeBillingEngine.balance(
            new BigDecimal("3750"), new BigDecimal("2850"));
        assertEquals(new BigDecimal("900.00"), balance);
    }

    @Test
    void 学期起始日不在月初时首月按半月() {
        LocalDate termStart = LocalDate.of(2026, 8, 15);
        assertEquals(new BigDecimal("0.50"),
            FeeBillingEngine.defaultFactor(termStart, YearMonth.of(2026, 8)));
        assertEquals(new BigDecimal("1.00"),
            FeeBillingEngine.defaultFactor(termStart, YearMonth.of(2026, 9)));
    }

    @Test
    void 学期起始日在月初时首月按整月() {
        LocalDate termStart = LocalDate.of(2026, 9, 1);
        assertEquals(new BigDecimal("1.00"),
            FeeBillingEngine.defaultFactor(termStart, YearMonth.of(2026, 9)));
    }

    @Test
    void 学期月份跨年按年月生成而不是按月份数字() {
        List<YearMonth> months = FeeBillingEngine.termMonths(
            LocalDate.of(2026, 8, 15), LocalDate.of(2027, 1, 31));
        assertEquals(List.of(
            YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10),
            YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1)), months);
    }

    @Test
    void 默认收费月数等于各月默认系数之和() {
        // 8 月半月 + 9~1 月五个整月 = 5.5，与源表的「收费月数 5.5」一致
        assertEquals(new BigDecimal("5.50"), FeeBillingEngine.defaultBillableMonths(
            LocalDate.of(2026, 8, 15), LocalDate.of(2027, 1, 31)));
    }

    @Test
    void 空值一律按零处理不抛空指针() {
        assertEquals(new BigDecimal("0.00"),
            FeeBillingEngine.receivable(null, null, null, null));
        assertEquals(new BigDecimal("0.00"),
            FeeBillingEngine.dailyOccurrence(null, null));
        assertEquals(BigDecimal.ZERO, FeeBillingEngine.nz(null));
    }
}
