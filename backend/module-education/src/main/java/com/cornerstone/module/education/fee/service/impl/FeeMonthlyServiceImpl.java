package com.cornerstone.module.education.fee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeMonthlyEntity;
import com.cornerstone.module.education.fee.dto.FeeMonthlyOverrideRequest;
import com.cornerstone.module.education.fee.dto.FeeMonthlyView;
import com.cornerstone.module.education.fee.dto.FeeRecalculateRequest;
import com.cornerstone.module.education.fee.mapper.FeeItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeMonthlyMapper;
import com.cornerstone.module.education.fee.service.FeeLedgerService;
import com.cornerstone.module.education.fee.service.FeeMonthlyService;
import com.cornerstone.module.education.fee.support.FeeBillingEngine;
import com.cornerstone.module.education.fee.support.FeeLedgerGuard;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeMonthlyServiceImpl implements FeeMonthlyService {

    private final FeeMonthlyMapper feeMonthlyMapper;
    private final FeeLedgerItemMapper feeLedgerItemMapper;
    private final FeeItemMapper feeItemMapper;
    private final FeeLedgerGuard feeLedgerGuard;
    private final FeeLedgerService feeLedgerService;

    public FeeMonthlyServiceImpl(FeeMonthlyMapper feeMonthlyMapper,
                                 FeeLedgerItemMapper feeLedgerItemMapper,
                                 FeeItemMapper feeItemMapper,
                                 FeeLedgerGuard feeLedgerGuard,
                                 FeeLedgerService feeLedgerService) {
        this.feeMonthlyMapper = feeMonthlyMapper;
        this.feeLedgerItemMapper = feeLedgerItemMapper;
        this.feeItemMapper = feeItemMapper;
        this.feeLedgerGuard = feeLedgerGuard;
        this.feeLedgerService = feeLedgerService;
    }

    @Override
    public List<FeeMonthlyView> listByLedger(Long ledgerId) {
        feeLedgerGuard.requireLedger(ledgerId);
        List<FeeLedgerItemEntity> items = feeLedgerItemMapper.selectList(
            new LambdaQueryWrapper<FeeLedgerItemEntity>()
                .eq(FeeLedgerItemEntity::getLedgerId, ledgerId));
        Map<Long, FeeItemEntity> feeItems = loadFeeItems(items);
        Map<Long, Integer> itemOrder = items.stream().collect(Collectors.toMap(
            FeeLedgerItemEntity::getFeeItemId,
            item -> feeItems.containsKey(item.getFeeItemId())
                ? feeItems.get(item.getFeeItemId()).getSortNo() : 0,
            (first, second) -> first));
        return feeMonthlyMapper.selectList(new LambdaQueryWrapper<FeeMonthlyEntity>()
                .eq(FeeMonthlyEntity::getLedgerId, ledgerId))
            .stream()
            .map(row -> toView(row, feeItems.get(itemIdOf(items, row))))
            .sorted(Comparator
                .comparing(FeeMonthlyView::billingMonth)
                .thenComparing(view -> itemOrder.getOrDefault(view.feeItemId(), 0))
                .thenComparing(FeeMonthlyView::feeItemId))
            .toList();
    }

    @Override
    public FeeMonthlyView override(Long ledgerItemId, LocalDate billingMonth,
                                   FeeMonthlyOverrideRequest request) {
        FeeLedgerItemEntity item = feeLedgerGuard.requireItem(ledgerItemId);
        LocalDate month = requireMonth(billingMonth);
        FeeMonthlyEntity row = feeMonthlyMapper.selectOne(new LambdaQueryWrapper<FeeMonthlyEntity>()
            .eq(FeeMonthlyEntity::getLedgerItemId, ledgerItemId)
            .eq(FeeMonthlyEntity::getBillingMonth, month));
        if (row == null) {
            row = new FeeMonthlyEntity();
            row.setInstitutionId(item.getInstitutionId());
            row.setCampusId(item.getCampusId());
            row.setLedgerId(item.getLedgerId());
            row.setLedgerItemId(item.getId());
            row.setBillingMonth(month);
            row.setMonthFactor(BigDecimal.ONE);
            row.setAutoAmount(FeeBillingEngine.money(BigDecimal.ZERO));
            row.setDeleted(Boolean.FALSE);
        }
        if (request.monthFactor() != null) {
            row.setMonthFactor(FeeBillingEngine.quantity(request.monthFactor()));
        }
        row.setOccurrenceAmount(FeeBillingEngine.money(request.occurrenceAmount()));
        row.setManualOverride(Boolean.TRUE);
        row.setOverrideBy(AuthContext.currentUser().map(user -> user.userId()).orElse(null));
        row.setOverrideAt(LocalDateTime.now());
        row.setOverrideReason(normalizeToNull(request.overrideReason()));
        if (row.getId() == null) {
            feeMonthlyMapper.insert(row);
        } else {
            feeMonthlyMapper.updateById(row);
        }
        return toView(row, feeItemMapper.selectById(item.getFeeItemId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeMonthlyView revert(Long ledgerItemId, LocalDate billingMonth) {
        FeeLedgerItemEntity item = feeLedgerGuard.requireItem(ledgerItemId);
        LocalDate month = requireMonth(billingMonth);
        FeeMonthlyEntity row = feeMonthlyMapper.selectOne(new LambdaQueryWrapper<FeeMonthlyEntity>()
            .eq(FeeMonthlyEntity::getLedgerItemId, ledgerItemId)
            .eq(FeeMonthlyEntity::getBillingMonth, month));
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该月还没有发生额记录，无法撤销覆盖");
        }
        if (!Boolean.TRUE.equals(row.getManualOverride())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该月发生额没有手工覆盖，无需撤销");
        }
        FeeLedgerEntity ledger = feeLedgerGuard.requireLedger(item.getLedgerId());
        TermEntity term = feeLedgerGuard.requireTerm(ledger.getTermId(), ledger.getInstitutionId());
        YearMonth yearMonth = YearMonth.from(month);
        if (!FeeBillingEngine.termMonths(term.getStartDate(), term.getEndDate()).contains(yearMonth)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "账期 " + yearMonth + " 不在学期「" + term.getTermName() + "」范围内，无法撤销覆盖");
        }
        // month_factor 是非空列，撤销只能回到默认口径，不能置空
        BigDecimal factor = FeeBillingEngine.defaultFactor(term.getStartDate(), yearMonth);
        int affected = feeMonthlyMapper.update(null, new LambdaUpdateWrapper<FeeMonthlyEntity>()
            .eq(FeeMonthlyEntity::getId, row.getId())
            .eq(FeeMonthlyEntity::getManualOverride, Boolean.TRUE)
            .set(FeeMonthlyEntity::getMonthFactor, factor)
            .set(FeeMonthlyEntity::getOccurrenceAmount, FeeBillingEngine.money(row.getAutoAmount()))
            .set(FeeMonthlyEntity::getManualOverride, Boolean.FALSE)
            .set(FeeMonthlyEntity::getOverrideBy, null)
            .set(FeeMonthlyEntity::getOverrideAt, null)
            .set(FeeMonthlyEntity::getOverrideReason, null));
        if (affected == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该月发生额覆盖已被其他操作撤销，请刷新后重试");
        }
        // 恢复默认系数与生效值后，按最新出勤重算整份台账，保证 autoAmount 与生效值一致
        feeLedgerService.recalculate(new FeeRecalculateRequest(item.getLedgerId(), null, null));
        return toView(feeMonthlyMapper.selectById(row.getId()),
            feeItemMapper.selectById(item.getFeeItemId()));
    }

    private Map<Long, FeeItemEntity> loadFeeItems(List<FeeLedgerItemEntity> items) {
        List<Long> itemIds = items.stream().map(FeeLedgerItemEntity::getFeeItemId).distinct().toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return feeItemMapper.selectBatchIds(itemIds).stream()
            .collect(Collectors.toMap(FeeItemEntity::getId, Function.identity(), (first, second) -> first));
    }

    private static Long itemIdOf(List<FeeLedgerItemEntity> items, FeeMonthlyEntity row) {
        return items.stream()
            .filter(item -> item.getId().equals(row.getLedgerItemId()))
            .map(FeeLedgerItemEntity::getFeeItemId)
            .findFirst()
            .orElse(null);
    }

    private FeeMonthlyView toView(FeeMonthlyEntity row, FeeItemEntity item) {
        return new FeeMonthlyView(row.getId(), row.getLedgerItemId(),
            item == null ? null : item.getId(),
            item == null ? null : item.getItemName(),
            item == null ? null : item.getBillingMode(),
            row.getBillingMonth(), row.getMonthFactor(), row.getAutoAmount(),
            row.getOccurrenceAmount(), Boolean.TRUE.equals(row.getManualOverride()), row.getRemark());
    }

    private LocalDate requireMonth(LocalDate month) {
        if (month == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "账期不能为空");
        }
        return month.withDayOfMonth(1);
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
