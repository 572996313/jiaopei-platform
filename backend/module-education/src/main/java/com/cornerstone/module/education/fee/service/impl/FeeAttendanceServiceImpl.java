package com.cornerstone.module.education.fee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeAttendanceMonthEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.dto.FeeAttendanceDayRow;
import com.cornerstone.module.education.fee.dto.FeeAttendanceOverrideRequest;
import com.cornerstone.module.education.fee.dto.FeeAttendanceSyncRequest;
import com.cornerstone.module.education.fee.dto.FeeAttendanceSyncResult;
import com.cornerstone.module.education.fee.dto.FeeAttendanceView;
import com.cornerstone.module.education.fee.dto.FeeRecalculateRequest;
import com.cornerstone.module.education.fee.mapper.FeeAttendanceMonthMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerMapper;
import com.cornerstone.module.education.fee.service.FeeAttendanceService;
import com.cornerstone.module.education.fee.service.FeeLedgerService;
import com.cornerstone.module.education.fee.support.FeeBillingEngine;
import com.cornerstone.module.education.fee.support.FeeLedgerGuard;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.TermMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeAttendanceServiceImpl implements FeeAttendanceService {

    private final FeeAttendanceMonthMapper feeAttendanceMonthMapper;
    private final FeeLedgerMapper feeLedgerMapper;
    private final TermMapper termMapper;
    private final FeeLedgerGuard feeLedgerGuard;
    private final FeeLedgerService feeLedgerService;

    public FeeAttendanceServiceImpl(FeeAttendanceMonthMapper feeAttendanceMonthMapper,
                                    FeeLedgerMapper feeLedgerMapper, TermMapper termMapper,
                                    FeeLedgerGuard feeLedgerGuard, FeeLedgerService feeLedgerService) {
        this.feeAttendanceMonthMapper = feeAttendanceMonthMapper;
        this.feeLedgerMapper = feeLedgerMapper;
        this.termMapper = termMapper;
        this.feeLedgerGuard = feeLedgerGuard;
        this.feeLedgerService = feeLedgerService;
    }

    @Override
    public List<FeeAttendanceView> listByLedger(Long ledgerId) {
        feeLedgerGuard.requireLedger(ledgerId);
        return feeAttendanceMonthMapper.selectList(new LambdaQueryWrapper<FeeAttendanceMonthEntity>()
                .eq(FeeAttendanceMonthEntity::getLedgerId, ledgerId)
                .orderByAsc(FeeAttendanceMonthEntity::getBillingMonth))
            .stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeAttendanceView override(Long ledgerId, LocalDate billingMonth,
                                      FeeAttendanceOverrideRequest request) {
        feeLedgerGuard.requireLedger(ledgerId);
        LocalDate month = requireMonth(billingMonth);
        FeeAttendanceMonthEntity row = feeAttendanceMonthMapper.selectOne(
            new LambdaQueryWrapper<FeeAttendanceMonthEntity>()
                .eq(FeeAttendanceMonthEntity::getLedgerId, ledgerId)
                .eq(FeeAttendanceMonthEntity::getBillingMonth, month));
        if (row == null) {
            FeeLedgerEntity ledger = feeLedgerGuard.requireLedger(ledgerId);
            row = new FeeAttendanceMonthEntity();
            row.setInstitutionId(ledger.getInstitutionId());
            row.setCampusId(ledger.getCampusId());
            row.setLedgerId(ledgerId);
            row.setBillingMonth(month);
            row.setAttendanceDays(BigDecimal.ZERO);
            row.setAutoDays(BigDecimal.ZERO);
            row.setDeleted(Boolean.FALSE);
        }
        row.setAttendanceDays(FeeBillingEngine.quantity(request.attendanceDays()));
        row.setManualOverride(Boolean.TRUE);
        row.setOverrideBy(AuthContext.currentUser().map(user -> user.userId()).orElse(null));
        row.setOverrideAt(LocalDateTime.now());
        row.setOverrideReason(normalizeToNull(request.overrideReason()));
        row.setSource("MANUAL");
        if (row.getId() == null) {
            feeAttendanceMonthMapper.insert(row);
        } else {
            feeAttendanceMonthMapper.updateById(row);
        }
        // 出勤变了，按天项目的发生额要跟着重算
        feeLedgerService.recalculate(new FeeRecalculateRequest(ledgerId, null, null));
        return toView(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeAttendanceView revert(Long ledgerId, LocalDate billingMonth) {
        feeLedgerGuard.requireLedger(ledgerId);
        LocalDate month = requireMonth(billingMonth);
        FeeAttendanceMonthEntity row = feeAttendanceMonthMapper.selectOne(
            new LambdaQueryWrapper<FeeAttendanceMonthEntity>()
                .eq(FeeAttendanceMonthEntity::getLedgerId, ledgerId)
                .eq(FeeAttendanceMonthEntity::getBillingMonth, month));
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该月还没有出勤记录，无法撤销覆盖");
        }
        if (!Boolean.TRUE.equals(row.getManualOverride())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该月出勤没有手工覆盖，无需撤销");
        }
        // 覆盖审计列可空，updateById 会跳过 null，必须用 UpdateWrapper 显式清空
        int affected = feeAttendanceMonthMapper.update(null,
            new LambdaUpdateWrapper<FeeAttendanceMonthEntity>()
                .eq(FeeAttendanceMonthEntity::getId, row.getId())
                .eq(FeeAttendanceMonthEntity::getManualOverride, Boolean.TRUE)
                .set(FeeAttendanceMonthEntity::getAttendanceDays,
                    FeeBillingEngine.quantity(row.getAutoDays()))
                .set(FeeAttendanceMonthEntity::getManualOverride, Boolean.FALSE)
                .set(FeeAttendanceMonthEntity::getSource, "AUTO")
                .set(FeeAttendanceMonthEntity::getOverrideBy, null)
                .set(FeeAttendanceMonthEntity::getOverrideAt, null)
                .set(FeeAttendanceMonthEntity::getOverrideReason, null));
        if (affected == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该月出勤覆盖已被其他操作撤销，请刷新后重试");
        }
        // 出勤回到自动值后，按天项目的发生额要跟着重算
        feeLedgerService.recalculate(new FeeRecalculateRequest(ledgerId, null, null));
        return toView(feeAttendanceMonthMapper.selectById(row.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeAttendanceSyncResult sync(FeeAttendanceSyncRequest request) {
        List<FeeLedgerEntity> ledgers = resolveLedgers(request);
        if (ledgers.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前范围内没有收费台账");
        }
        Map<Long, TermEntity> terms = ledgers.stream()
            .map(FeeLedgerEntity::getTermId).distinct()
            .map(termMapper::selectById)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(TermEntity::getId, Function.identity(), (first, second) -> first));

        Map<String, FeeAttendanceMonthEntity> existing = feeAttendanceMonthMapper.selectList(
                new LambdaQueryWrapper<FeeAttendanceMonthEntity>()
                    .in(FeeAttendanceMonthEntity::getLedgerId,
                        ledgers.stream().map(FeeLedgerEntity::getId).toList()))
            .stream()
            .collect(Collectors.toMap(
                row -> row.getLedgerId() + "|" + row.getBillingMonth(),
                Function.identity(), (first, second) -> first));

        Map<String, Map<Long, Long>> dayCache = new HashMap<>();
        int synced = 0;
        int keptOverrides = 0;

        for (FeeLedgerEntity ledger : ledgers) {
            TermEntity term = terms.get(ledger.getTermId());
            if (term == null || term.getStartDate() == null || term.getEndDate() == null) {
                continue;
            }
            for (YearMonth month : FeeBillingEngine.termMonths(term.getStartDate(), term.getEndDate())) {
                Map<Long, Long> daysByStudent = dayCache.computeIfAbsent(
                    ledger.getClassId() + "|" + month,
                    key -> loadDays(ledger, month));
                long autoDays = daysByStudent.getOrDefault(ledger.getStudentId(), 0L);
                LocalDate monthFirst = month.atDay(1);
                FeeAttendanceMonthEntity row = existing.get(ledger.getId() + "|" + monthFirst);
                if (row == null) {
                    row = new FeeAttendanceMonthEntity();
                    row.setInstitutionId(ledger.getInstitutionId());
                    row.setCampusId(ledger.getCampusId());
                    row.setLedgerId(ledger.getId());
                    row.setBillingMonth(monthFirst);
                    row.setAttendanceDays(BigDecimal.ZERO);
                    row.setAutoDays(BigDecimal.ZERO);
                    row.setManualOverride(Boolean.FALSE);
                    row.setDeleted(Boolean.FALSE);
                    row.setSource("AUTO");
                    existing.put(ledger.getId() + "|" + monthFirst, row);
                }
                row.setAutoDays(FeeBillingEngine.quantity(BigDecimal.valueOf(autoDays)));
                if (Boolean.TRUE.equals(row.getManualOverride())) {
                    keptOverrides++;
                } else {
                    row.setAttendanceDays(FeeBillingEngine.quantity(BigDecimal.valueOf(autoDays)));
                    row.setSource("AUTO");
                }
                if (row.getId() == null) {
                    feeAttendanceMonthMapper.insert(row);
                } else {
                    feeAttendanceMonthMapper.updateById(row);
                }
                synced++;
            }
        }

        for (FeeLedgerEntity ledger : ledgers) {
            feeLedgerService.recalculate(new FeeRecalculateRequest(ledger.getId(), null, null));
        }
        return new FeeAttendanceSyncResult(ledgers.size(), synced, keptOverrides);
    }

    private List<FeeLedgerEntity> resolveLedgers(FeeAttendanceSyncRequest request) {
        if (request.ledgerId() != null) {
            return List.of(feeLedgerGuard.requireLedger(request.ledgerId()));
        }
        if (request.termId() == null || request.classId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请指定台账，或同时指定学期与班级");
        }
        Long institutionId = feeLedgerGuard.resolveInstitution(null);
        TeachingClassEntity clazz = feeLedgerGuard.requireClass(request.classId(), institutionId);
        feeLedgerGuard.requireTerm(request.termId(), institutionId);
        return feeLedgerMapper.selectList(new LambdaQueryWrapper<FeeLedgerEntity>()
            .eq(FeeLedgerEntity::getInstitutionId, institutionId)
            .eq(FeeLedgerEntity::getTermId, request.termId())
            .eq(FeeLedgerEntity::getClassId, clazz.getId()));
    }

    private Map<Long, Long> loadDays(FeeLedgerEntity ledger, YearMonth month) {
        List<FeeAttendanceDayRow> rows = feeAttendanceMonthMapper.sumDaysByClassAndMonth(
            ledger.getInstitutionId(), ledger.getClassId(),
            month.atDay(1), month.plusMonths(1).atDay(1));
        return rows.stream().collect(Collectors.toMap(
            FeeAttendanceDayRow::getStudentId,
            row -> row.getDayCount() == null ? 0L : row.getDayCount(),
            (first, second) -> first));
    }

    private LocalDate requireMonth(LocalDate month) {
        if (month == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "账期不能为空");
        }
        return month.withDayOfMonth(1);
    }

    private FeeAttendanceView toView(FeeAttendanceMonthEntity entity) {
        return new FeeAttendanceView(entity.getId(), entity.getBillingMonth(),
            entity.getAttendanceDays(), entity.getAutoDays(),
            Boolean.TRUE.equals(entity.getManualOverride()), entity.getSource(), entity.getRemark());
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
