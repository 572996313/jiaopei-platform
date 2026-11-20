package com.cornerstone.module.education.fee.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 收费计算口径的唯一实现（纯函数，不连库，便于单测钉住）。
 *
 * <p>与源表逐格核对过的公式：
 * <pre>
 *   应收     = 收费月数 × 月标准 − 上期结余 − 其他抵扣
 *   未收费   = 应收 − 实收（负数 = 预收/多收，合法）
 *   按月发生 = 出勤天数 &gt; 0 ? 月标准 × 月份系数 : 0
 *   按天发生 = 出勤天数 × 日标准
 *   结余     = 实收 − 合计发生（正数即可退金额）
 * </pre>
 *
 * <p>金额一律 {@code BigDecimal} + {@code HALF_UP} 两位小数，禁用 double；
 * 月数/天数/系数保留两位。所有入参 null 都按 0 处理，避免页面上留空就 NPE。</p>
 */
public final class FeeBillingEngine {

    /** 金额统一两位小数，与库里的 DECIMAL(x,2) 对齐 */
    private static final int MONEY_SCALE = 2;

    /** 月数/天数/系数两位小数，与库里的 DECIMAL(5,2)/(4,2) 对齐 */
    private static final int QUANTITY_SCALE = 2;

    /** 学期起始日不在月初时，首月默认按半月计 */
    private static final BigDecimal HALF_MONTH = new BigDecimal("0.50");

    private static final BigDecimal FULL_MONTH = new BigDecimal("1.00");

    private FeeBillingEngine() {
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal money(BigDecimal value) {
        return nz(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal quantity(BigDecimal value) {
        return nz(value).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    /** 应收 = 收费月数 × 月标准 − 上期结余 − 其他抵扣。 */
    public static BigDecimal receivable(BigDecimal billableMonths, BigDecimal monthlyStandard,
                                        BigDecimal openingBalance, BigDecimal otherDeduction) {
        return money(quantity(billableMonths).multiply(money(monthlyStandard))
            .subtract(money(openingBalance))
            .subtract(money(otherDeduction)));
    }

    /** 未收费 = 应收 − 实收。负数表示预收/多收，不是错误。 */
    public static BigDecimal unpaid(BigDecimal receivable, BigDecimal paid) {
        return money(receivable).subtract(money(paid)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 按月项目的当月发生额。
     *
     * <p>出勤为 0 默认 0：没来就不消耗学费（温瑾瑜 6 个月月数、0 出勤、发生 0 是真实案例）。
     * 真要收整月，财务在逐月发生额上手工覆盖即可。</p>
     */
    public static BigDecimal monthlyOccurrence(BigDecimal monthlyStandard, BigDecimal monthFactor,
                                               BigDecimal attendanceDays) {
        if (quantity(attendanceDays).signum() <= 0) {
            return money(BigDecimal.ZERO);
        }
        return money(money(monthlyStandard).multiply(quantity(monthFactor)));
    }

    /** 按天项目的当月发生额 = 出勤天数 × 日标准。 */
    public static BigDecimal dailyOccurrence(BigDecimal dailyRate, BigDecimal attendanceDays) {
        return money(quantity(attendanceDays).multiply(money(dailyRate)));
    }

    /** 结余 = 实收 − 合计发生，正数即可退金额。 */
    public static BigDecimal balance(BigDecimal paid, BigDecimal totalOccurrence) {
        return money(paid).subtract(money(totalOccurrence)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 首月系数：学期起始日不在月初时默认 0.50（源表 8 月保教费 750 = 1500 × 0.5），其余整月 1.00。
     * 只看学期起始月，中途插班不自动折半月——那属学员个案，财务手工改。
     */
    public static BigDecimal defaultFactor(LocalDate termStart, YearMonth month) {
        if (termStart == null || month == null) {
            return FULL_MONTH;
        }
        if (YearMonth.from(termStart).equals(month) && termStart.getDayOfMonth() > 1) {
            return HALF_MONTH;
        }
        return FULL_MONTH;
    }

    /** 学期覆盖的月份列表（含首尾月）；起止为空或倒置时返回空列表。 */
    public static List<YearMonth> termMonths(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return List.of();
        }
        YearMonth first = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    /** 默认收费月数 = 各月默认系数之和（8 月中开学、学期到 1 月 = 5.5）。 */
    public static BigDecimal defaultBillableMonths(LocalDate start, LocalDate end) {
        BigDecimal total = BigDecimal.ZERO;
        for (YearMonth month : termMonths(start, end)) {
            total = total.add(defaultFactor(start, month));
        }
        return quantity(total);
    }
}
