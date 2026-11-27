package com.cornerstone.module.education.fee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeMonthlyEntity;
import com.cornerstone.module.education.fee.domain.entity.FeePaymentEntity;
import com.cornerstone.module.education.fee.dto.MiniappFeeItemView;
import com.cornerstone.module.education.fee.dto.MiniappFeeLedgerView;
import com.cornerstone.module.education.fee.dto.MiniappFeePaymentView;
import com.cornerstone.module.education.fee.dto.MiniappStudentFeeView;
import com.cornerstone.module.education.fee.mapper.FeeItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerMapper;
import com.cornerstone.module.education.fee.mapper.FeeMonthlyMapper;
import com.cornerstone.module.education.fee.mapper.FeePaymentMapper;
import com.cornerstone.module.education.fee.service.StudentFeeQueryService;
import com.cornerstone.module.education.fee.support.FeeBillingEngine;
import com.cornerstone.module.education.fee.support.FeeLedgerCalculator;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.TermMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 家长端费用查询实现。
 *
 * <p>派生值计算一律复用 {@link FeeLedgerCalculator} 与 {@link FeeBillingEngine}，
 * 与后台台账列表/详情/导出保持同一套公式——小程序侧绝不另写一份。</p>
 */
@Service
public class StudentFeeQueryServiceImpl implements StudentFeeQueryService {

    /** 家长端只展示最近 50 笔收款：足够核对，又不至于把页面拉成一整本流水账 */
    private static final int MAX_PAYMENT_ROWS = 50;

    private final FeeLedgerMapper feeLedgerMapper;
    private final FeeLedgerItemMapper feeLedgerItemMapper;
    private final FeeMonthlyMapper feeMonthlyMapper;
    private final FeePaymentMapper feePaymentMapper;
    private final FeeItemMapper feeItemMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final TermMapper termMapper;

    public StudentFeeQueryServiceImpl(FeeLedgerMapper feeLedgerMapper,
                                      FeeLedgerItemMapper feeLedgerItemMapper,
                                      FeeMonthlyMapper feeMonthlyMapper,
                                      FeePaymentMapper feePaymentMapper,
                                      FeeItemMapper feeItemMapper,
                                      TeachingClassMapper teachingClassMapper,
                                      TermMapper termMapper) {
        this.feeLedgerMapper = feeLedgerMapper;
        this.feeLedgerItemMapper = feeLedgerItemMapper;
        this.feeMonthlyMapper = feeMonthlyMapper;
        this.feePaymentMapper = feePaymentMapper;
        this.feeItemMapper = feeItemMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.termMapper = termMapper;
    }

    @Override
    public MiniappStudentFeeView studentFees(Long studentId) {
        List<FeeLedgerEntity> ledgers = feeLedgerMapper.selectList(new LambdaQueryWrapper<FeeLedgerEntity>()
            .eq(FeeLedgerEntity::getStudentId, studentId)
            .orderByDesc(FeeLedgerEntity::getId));
        if (ledgers.isEmpty()) {
            return emptyView(studentId);
        }

        List<Long> ledgerIds = ledgers.stream().map(FeeLedgerEntity::getId).toList();
        List<FeeLedgerItemEntity> items = feeLedgerItemMapper.selectList(
            new LambdaQueryWrapper<FeeLedgerItemEntity>().in(FeeLedgerItemEntity::getLedgerId, ledgerIds));
        List<FeeMonthlyEntity> monthly = feeMonthlyMapper.selectList(
            new LambdaQueryWrapper<FeeMonthlyEntity>().in(FeeMonthlyEntity::getLedgerId, ledgerIds));
        List<FeePaymentEntity> payments = feePaymentMapper.selectList(
            new LambdaQueryWrapper<FeePaymentEntity>().in(FeePaymentEntity::getLedgerId, ledgerIds));

        Map<Long, BigDecimal> paidByItem = FeeLedgerCalculator.paidByItem(payments);
        Map<Long, BigDecimal> occurrenceByItem = FeeLedgerCalculator.occurrenceByItem(monthly);
        Map<Long, List<FeeLedgerItemEntity>> itemsByLedger = items.stream()
            .collect(Collectors.groupingBy(FeeLedgerItemEntity::getLedgerId));
        Map<Long, FeeItemEntity> feeItems = loadFeeItems(items);
        Map<Long, TeachingClassEntity> classes = loadClasses(ledgers);
        Map<Long, TermEntity> terms = loadTerms(ledgers);

        BigDecimal receivableSum = BigDecimal.ZERO;
        BigDecimal paidSum = BigDecimal.ZERO;
        BigDecimal occurrenceSum = BigDecimal.ZERO;
        List<MiniappFeeLedgerView> ledgerViews = new ArrayList<>();
        for (FeeLedgerEntity ledger : ledgers) {
            List<FeeLedgerItemEntity> ledgerItems = itemsByLedger.getOrDefault(ledger.getId(), List.of())
                .stream()
                .sorted(Comparator
                    .comparing((FeeLedgerItemEntity item) -> sortNoOf(feeItems, item))
                    .thenComparing(FeeLedgerItemEntity::getId))
                .toList();
            BigDecimal receivable = BigDecimal.ZERO;
            BigDecimal paid = BigDecimal.ZERO;
            BigDecimal occurrence = BigDecimal.ZERO;
            List<MiniappFeeItemView> itemViews = new ArrayList<>();
            for (FeeLedgerItemEntity item : ledgerItems) {
                BigDecimal itemReceivable = itemReceivable(item);
                BigDecimal itemPaid = paidByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
                receivable = receivable.add(itemReceivable);
                paid = paid.add(itemPaid);
                occurrence = occurrence.add(occurrenceByItem.getOrDefault(item.getId(), BigDecimal.ZERO));
                FeeItemEntity feeItem = feeItems.get(item.getFeeItemId());
                itemViews.add(new MiniappFeeItemView(
                    feeItem == null ? null : feeItem.getItemName(),
                    item.getBillingMode(),
                    item.getBillableMonths(),
                    item.getMonthlyStandard(),
                    itemReceivable,
                    FeeBillingEngine.money(itemPaid),
                    FeeBillingEngine.unpaid(itemReceivable, itemPaid)));
            }
            receivableSum = receivableSum.add(receivable);
            paidSum = paidSum.add(paid);
            occurrenceSum = occurrenceSum.add(occurrence);

            TeachingClassEntity clazz = classes.get(ledger.getClassId());
            TermEntity term = terms.get(ledger.getTermId());
            ledgerViews.add(new MiniappFeeLedgerView(
                ledger.getId(),
                clazz == null ? null : clazz.getClassName(),
                term == null ? null : term.getTermName(),
                ledger.getStatus(),
                FeeBillingEngine.money(receivable),
                FeeBillingEngine.money(paid),
                FeeBillingEngine.unpaid(receivable, paid),
                FeeBillingEngine.balance(paid, occurrence),
                itemViews));
        }

        return new MiniappStudentFeeView(
            studentId,
            FeeBillingEngine.money(receivableSum),
            FeeBillingEngine.money(paidSum),
            FeeBillingEngine.unpaid(receivableSum, paidSum),
            FeeBillingEngine.balance(paidSum, occurrenceSum),
            ledgerViews,
            toPaymentViews(payments));
    }

    /** 家长端不展示凭证号与内部备注，只保留核对金额需要的字段 */
    private List<MiniappFeePaymentView> toPaymentViews(List<FeePaymentEntity> payments) {
        return payments.stream()
            .sorted(Comparator
                .comparing(FeePaymentEntity::getPaidOn, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FeePaymentEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(MAX_PAYMENT_ROWS)
            .map(row -> new MiniappFeePaymentView(
                row.getId(), row.getPaymentNo(), row.getAmount(), row.getPaidOn(),
                row.getPaymentMethod(), row.getBillingMonth()))
            .toList();
    }

    private MiniappStudentFeeView emptyView(Long studentId) {
        BigDecimal zero = FeeBillingEngine.money(BigDecimal.ZERO);
        return new MiniappStudentFeeView(studentId, zero, zero, zero, zero, List.of(), List.of());
    }

    /** 与后台台账同一公式：应收 = 收费月数 × 月标准 − 上期结余 − 其他抵扣 */
    private BigDecimal itemReceivable(FeeLedgerItemEntity item) {
        return FeeBillingEngine.receivable(item.getBillableMonths(), item.getMonthlyStandard(),
            item.getOpeningBalance(), item.getOtherDeduction());
    }

    private int sortNoOf(Map<Long, FeeItemEntity> feeItems, FeeLedgerItemEntity item) {
        FeeItemEntity feeItem = feeItems.get(item.getFeeItemId());
        return feeItem == null || feeItem.getSortNo() == null ? Integer.MAX_VALUE : feeItem.getSortNo();
    }

    private Map<Long, FeeItemEntity> loadFeeItems(List<FeeLedgerItemEntity> items) {
        List<Long> feeItemIds = items.stream().map(FeeLedgerItemEntity::getFeeItemId)
            .filter(Objects::nonNull).distinct().toList();
        if (feeItemIds.isEmpty()) {
            return Map.of();
        }
        return feeItemMapper.selectBatchIds(feeItemIds).stream()
            .collect(Collectors.toMap(FeeItemEntity::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, TeachingClassEntity> loadClasses(List<FeeLedgerEntity> ledgers) {
        List<Long> classIds = ledgers.stream().map(FeeLedgerEntity::getClassId)
            .filter(Objects::nonNull).distinct().toList();
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return teachingClassMapper.selectBatchIds(classIds).stream()
            .collect(Collectors.toMap(TeachingClassEntity::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, TermEntity> loadTerms(List<FeeLedgerEntity> ledgers) {
        List<Long> termIds = ledgers.stream().map(FeeLedgerEntity::getTermId)
            .filter(Objects::nonNull).distinct().toList();
        if (termIds.isEmpty()) {
            return Map.of();
        }
        return termMapper.selectBatchIds(termIds).stream()
            .collect(Collectors.toMap(TermEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
