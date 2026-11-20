package com.cornerstone.module.education.fee.support;

import com.cornerstone.module.education.fee.domain.entity.FeeMonthlyEntity;
import com.cornerstone.module.education.fee.domain.entity.FeePaymentEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 台账派生值计算：实收、逐月发生、结余一律从子表现算，不落库。
 *
 * <p>这是本业务域最重要的一条设计约束：台账头一旦存"实收/合计发生"，
 * 删一笔收款或重算一次发生额就会出现两本账。列表、详情、导出三处共用这里。</p>
 */
public final class FeeLedgerCalculator {

    private FeeLedgerCalculator() {
    }

    /** 某台账明细的实收合计；无收款返回 0.00。 */
    public static BigDecimal paidOf(List<FeePaymentEntity> payments, Long ledgerItemId) {
        return FeeBillingEngine.money(payments.stream()
            .filter(payment -> Objects.equals(payment.getLedgerItemId(), ledgerItemId))
            .map(FeePaymentEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** 按台账明细分组的实收合计，导出/详情一次算全，避免逐行扫列表。 */
    public static Map<Long, BigDecimal> paidByItem(List<FeePaymentEntity> payments) {
        return payments.stream().collect(Collectors.groupingBy(
            FeePaymentEntity::getLedgerItemId,
            Collectors.reducing(BigDecimal.ZERO,
                payment -> FeeBillingEngine.nz(payment.getAmount()), BigDecimal::add)));
    }

    /** 某台账明细的合计发生额；无逐月记录返回 0.00。 */
    public static BigDecimal occurrenceOf(List<FeeMonthlyEntity> monthly, Long ledgerItemId) {
        return FeeBillingEngine.money(monthly.stream()
            .filter(row -> Objects.equals(row.getLedgerItemId(), ledgerItemId))
            .map(FeeMonthlyEntity::getOccurrenceAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** 按台账明细分组的合计发生额。 */
    public static Map<Long, BigDecimal> occurrenceByItem(List<FeeMonthlyEntity> monthly) {
        return monthly.stream().collect(Collectors.groupingBy(
            FeeMonthlyEntity::getLedgerItemId,
            Collectors.reducing(BigDecimal.ZERO,
                row -> FeeBillingEngine.nz(row.getOccurrenceAmount()), BigDecimal::add)));
    }
}
