package com.cornerstone.module.education.fee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.fee.domain.entity.FeeAttendanceMonthEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeMonthlyEntity;
import com.cornerstone.module.education.fee.dto.FeeMonthlyOverrideRequest;
import com.cornerstone.module.education.fee.dto.FeeMonthlyView;
import com.cornerstone.module.education.fee.dto.FeeRecalculateRequest;
import com.cornerstone.module.education.fee.mapper.FeeAttendanceMonthMapper;
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

    private static final String DAILY = "daily";

    private final FeeMonthlyMapper feeMonthlyMapper;
    private final FeeLedgerItemMapper feeLedgerItemMapper;
    private final FeeItemMapper feeItemMapper;
    private final FeeAttendanceMonthMapper feeAttendanceMonthMapper;
    private final FeeLedgerGuard feeLedgerGuard;
    private final FeeLedgerService feeLedgerService;

    public FeeMonthlyServiceImpl(FeeMonthlyMapper feeMonthlyMapper,
                                 FeeLedgerItemMapper feeLedgerItemMapper,
                                 FeeItemMapper feeItemMapper,
                                 FeeAttendanceMonthMapper feeAttendanceMonthMapper,
                                 FeeLedgerGuard feeLedgerGuard,
                                 FeeLedgerService feeLedgerService) {
        this.feeMonthlyMapper = feeMonthlyMapper;
        this.feeLedgerItemMapper = feeLedgerItemMapper;
        this.feeItemMapper = feeItemMapper;
        this.feeAttendanceMonthMapper = feeAttendanceMonthMapper;
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
        boolean followAuto = request.occurrenceAmount() == null;
        if (request.monthFactor() != null) {
            row.setMonthFactor(FeeBillingEngine.factor(request.monthFactor()));
        }
        if (followAuto) {
            // 只改了月份系数：生效发生额跟随引擎重算，不产生金额覆盖。
            // 这样「填 0.5 保存」得到的是 750，而不是把旧生效值 1500 一起钉成手工覆盖。
            BigDecimal auto = autoAmount(item, month, row.getMonthFactor());
            row.setAutoAmount(auto);
            row.setOccurrenceAmount(auto);
            row.setManualOverride(Boolean.FALSE);
            row.setOverrideBy(null);
            row.setOverrideAt(null);
            row.setOverrideReason(null);
        } else {
            row.setOccurrenceAmount(FeeBillingEngine.money(request.occurrenceAmount()));
            row.setManualOverride(Boolean.TRUE);
            row.setOverrideBy(AuthContext.currentUser().map(user -> user.userId()).orElse(null));
            row.setOverrideAt(LocalDateTime.now());
            row.setOverrideReason(normalizeToNull(request.overrideReason()));
        }
        if (row.getId() == null) {
            feeMonthlyMapper.insert(row);
        } else if (followAuto) {
            // updateById 默认忽略 null，清覆盖审计列必须走 wrapper 的 set(..., null)
            feeMonthlyMapper.update(null, new LambdaUpdateWrapper<FeeMonthlyEntity>()
                .eq(FeeMonthlyEntity::getId, row.getId())
                .set(FeeMonthlyEntity::getMonthFactor, row.getMonthFactor())
                .set(FeeMonthlyEntity::getAutoAmount, row.getAutoAmount())
                .set(FeeMonthlyEntity::getOccurrenceAmount, row.getOccurrenceAmount())
                .set(FeeMonthlyEntity::getManualOverride, Boolean.FALSE)
                .set(FeeMonthlyEntity::getOverrideBy, null)
                .set(FeeMonthlyEntity::getOverrideAt, null)
                .set(FeeMonthlyEntity::getOverrideReason, null));
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
        // 只撤「生效发生额」的覆盖：月份系数是运营按学员个案手工调好的口径（如半月 0.50），
        // 撤销金额覆盖不该把它一起打回学期默认值。清掉标记后由重算把生效值改回引擎自动值。
        int affected = feeMonthlyMapper.update(null, new LambdaUpdateWrapper<FeeMonthlyEntity>()
            .eq(FeeMonthlyEntity::getId, row.getId())
            .eq(FeeMonthlyEntity::getManualOverride, Boolean.TRUE)
            .set(FeeMonthlyEntity::getOccurrenceAmount, FeeBillingEngine.money(row.getAutoAmount()))
            .set(FeeMonthlyEntity::getManualOverride, Boolean.FALSE)
            .set(FeeMonthlyEntity::getOverrideBy, null)
            .set(FeeMonthlyEntity::getOverrideAt, null)
            .set(FeeMonthlyEntity::getOverrideReason, null));
        if (affected == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该月发生额覆盖已被其他操作撤销，请刷新后重试");
        }
        // 系数保留，重算只刷新 autoAmount 并把生效值对齐到引擎口径
        feeLedgerService.recalculate(new FeeRecalculateRequest(item.getLedgerId(), null, null));
        return toView(feeMonthlyMapper.selectById(row.getId()),
            feeItemMapper.selectById(item.getFeeItemId()));
    }

    /** 与 rebuildMonthly 同一口径：按月 = 月标准 × 系数（当月无出勤为 0），按天 = 出勤天数 × 日标准。 */
    private BigDecimal autoAmount(FeeLedgerItemEntity item, LocalDate month, BigDecimal factor) {
        FeeAttendanceMonthEntity attendance = feeAttendanceMonthMapper.selectOne(
            new LambdaQueryWrapper<FeeAttendanceMonthEntity>()
                .eq(FeeAttendanceMonthEntity::getLedgerId, item.getLedgerId())
                .eq(FeeAttendanceMonthEntity::getBillingMonth, month));
        BigDecimal days = attendance == null ? BigDecimal.ZERO : attendance.getAttendanceDays();
        return DAILY.equals(item.getBillingMode())
            ? FeeBillingEngine.dailyOccurrence(item.getDailyRate(), days)
            : FeeBillingEngine.monthlyOccurrence(item.getMonthlyStandard(), factor, days);
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
