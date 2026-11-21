package com.cornerstone.module.education;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.cornerstone.module.education.fee.service.impl.FeeMonthlyServiceImpl;
import com.cornerstone.module.education.fee.support.FeeLedgerGuard;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 逐月发生额：月份系数是运营手工调的口径，「撤销覆盖」只把生效发生额恢复为引擎自动值，不动系数。
 */
class FeeMonthlyRevertTest {

    private static final Long ITEM_ID = 600L;
    private static final Long LEDGER_ID = 700L;
    private static final Long TERM_ID = 300L;
    private static final Long INSTITUTION_ID = 100L;
    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 1);
    private static final LocalDate OUTSIDE_TERM = LocalDate.of(2026, 7, 1);

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FeeMonthlyEntity.class);
        TableInfoHelper.initTableInfo(assistant, FeeAttendanceMonthEntity.class);
    }

    private final FeeMonthlyMapper monthlyMapper = mock(FeeMonthlyMapper.class);
    private final FeeLedgerItemMapper ledgerItemMapper = mock(FeeLedgerItemMapper.class);
    private final FeeItemMapper feeItemMapper = mock(FeeItemMapper.class);
    private final FeeAttendanceMonthMapper attendanceMapper = mock(FeeAttendanceMonthMapper.class);
    private final FeeLedgerGuard guard = mock(FeeLedgerGuard.class);
    private final FeeLedgerService ledgerService = mock(FeeLedgerService.class);
    private final FeeMonthlyServiceImpl service = new FeeMonthlyServiceImpl(
        monthlyMapper, ledgerItemMapper, feeItemMapper, attendanceMapper, guard, ledgerService);

    @Test
    void revertKeepsMonthFactorAndRestoresAutoAmount() {
        FeeMonthlyEntity overridden = row(true);
        overridden.setMonthFactor(new BigDecimal("0.50"));
        FeeMonthlyEntity reverted = row(false);
        reverted.setMonthFactor(new BigDecimal("0.50"));
        reverted.setOccurrenceAmount(new BigDecimal("750.00"));
        when(guard.requireItem(ITEM_ID)).thenReturn(item());
        when(monthlyMapper.selectOne(any())).thenReturn(overridden);
        when(guard.requireLedger(LEDGER_ID)).thenReturn(ledger());
        when(guard.requireTerm(TERM_ID, INSTITUTION_ID)).thenReturn(term());
        when(monthlyMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(monthlyMapper.selectById(1L)).thenReturn(reverted);
        when(feeItemMapper.selectById(10L)).thenReturn(feeItem());

        FeeMonthlyView view = service.revert(ITEM_ID, AUGUST);

        assertEquals(new BigDecimal("0.50"), view.monthFactor());
        assertEquals(new BigDecimal("750.00"), view.occurrenceAmount());
        assertFalse(view.manualOverride());
        verify(ledgerService).recalculate(any(FeeRecalculateRequest.class));
    }

    @Test
    void overrideWithFactorOnlyFollowsEngineWithoutManualFlag() {
        FeeAttendanceMonthEntity attendance = new FeeAttendanceMonthEntity();
        attendance.setAttendanceDays(new BigDecimal("20"));
        FeeMonthlyEntity row = row(false);
        row.setMonthFactor(BigDecimal.ONE);
        row.setAutoAmount(new BigDecimal("1500.00"));
        row.setOccurrenceAmount(new BigDecimal("1500.00"));
        when(guard.requireItem(ITEM_ID)).thenReturn(item());
        when(monthlyMapper.selectOne(any())).thenReturn(row);
        when(attendanceMapper.selectOne(any())).thenReturn(attendance);
        when(feeItemMapper.selectById(10L)).thenReturn(feeItem());

        FeeMonthlyView view = service.override(ITEM_ID, AUGUST,
            new FeeMonthlyOverrideRequest(null, new BigDecimal("0.50"), null));

        assertFalse(view.manualOverride());
        assertEquals(new BigDecimal("0.5000"), view.monthFactor());
        assertEquals(new BigDecimal("750.0000"), view.autoAmount());
        assertEquals(new BigDecimal("750.0000"), view.occurrenceAmount());
    }

    @Test
    void overrideWithAmountStillMarksManual() {
        FeeMonthlyEntity row = row(false);
        when(guard.requireItem(ITEM_ID)).thenReturn(item());
        when(monthlyMapper.selectOne(any())).thenReturn(row);
        when(feeItemMapper.selectById(10L)).thenReturn(feeItem());

        FeeMonthlyView view = service.override(ITEM_ID, AUGUST,
            new FeeMonthlyOverrideRequest(new BigDecimal("500.00"), null, "优惠"));

        assertTrue(view.manualOverride());
        assertEquals(new BigDecimal("500.0000"), view.occurrenceAmount());
    }

    @Test
    void revertRejectsWhenRowIsNotManuallyOverridden() {
        when(guard.requireItem(ITEM_ID)).thenReturn(item());
        when(monthlyMapper.selectOne(any())).thenReturn(row(false));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.revert(ITEM_ID, AUGUST));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        verify(monthlyMapper, never()).update(any(), any(Wrapper.class));
        verify(ledgerService, never()).recalculate(any());
    }

    @Test
    void revertRejectsMonthOutsideTermRange() {
        when(guard.requireItem(ITEM_ID)).thenReturn(item());
        when(monthlyMapper.selectOne(any())).thenReturn(row(true));
        when(guard.requireLedger(LEDGER_ID)).thenReturn(ledger());
        when(guard.requireTerm(TERM_ID, INSTITUTION_ID)).thenReturn(term());

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.revert(ITEM_ID, OUTSIDE_TERM));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        verify(monthlyMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void revertFailsWhenConcurrentChangeAlreadyClearedOverride() {
        when(guard.requireItem(ITEM_ID)).thenReturn(item());
        when(monthlyMapper.selectOne(any())).thenReturn(row(true));
        when(guard.requireLedger(LEDGER_ID)).thenReturn(ledger());
        when(guard.requireTerm(TERM_ID, INSTITUTION_ID)).thenReturn(term());
        when(monthlyMapper.update(any(), any(Wrapper.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.revert(ITEM_ID, AUGUST));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        verify(ledgerService, never()).recalculate(any());
    }

    private FeeLedgerItemEntity item() {
        FeeLedgerItemEntity item = new FeeLedgerItemEntity();
        item.setId(ITEM_ID);
        item.setLedgerId(LEDGER_ID);
        item.setFeeItemId(10L);
        item.setInstitutionId(INSTITUTION_ID);
        item.setBillingMode("monthly");
        item.setMonthlyStandard(new BigDecimal("1500.00"));
        return item;
    }

    private FeeLedgerEntity ledger() {
        FeeLedgerEntity ledger = new FeeLedgerEntity();
        ledger.setId(LEDGER_ID);
        ledger.setTermId(TERM_ID);
        ledger.setInstitutionId(INSTITUTION_ID);
        return ledger;
    }

    private TermEntity term() {
        TermEntity term = new TermEntity();
        term.setId(TERM_ID);
        term.setTermName("2026 秋季学期");
        term.setInstitutionId(INSTITUTION_ID);
        term.setStartDate(LocalDate.of(2026, 8, 15));
        term.setEndDate(LocalDate.of(2027, 1, 31));
        return term;
    }

    private FeeItemEntity feeItem() {
        FeeItemEntity item = new FeeItemEntity();
        item.setId(10L);
        item.setItemName("保教费");
        item.setBillingMode("monthly");
        return item;
    }

    private FeeMonthlyEntity row(boolean manual) {
        FeeMonthlyEntity row = new FeeMonthlyEntity();
        row.setId(1L);
        row.setLedgerId(LEDGER_ID);
        row.setLedgerItemId(ITEM_ID);
        row.setBillingMonth(AUGUST);
        row.setMonthFactor(BigDecimal.ONE);
        row.setAutoAmount(new BigDecimal("750.00"));
        row.setOccurrenceAmount(new BigDecimal("500.00"));
        row.setManualOverride(manual);
        return row;
    }
}
