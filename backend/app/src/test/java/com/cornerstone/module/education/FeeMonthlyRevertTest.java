package com.cornerstone.module.education;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeMonthlyEntity;
import com.cornerstone.module.education.fee.dto.FeeMonthlyView;
import com.cornerstone.module.education.fee.dto.FeeRecalculateRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 逐月发生额撤销：月份系数回到学期默认口径（首月 0.50），并清除手工覆盖审计列。
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
    }

    private final FeeMonthlyMapper monthlyMapper = mock(FeeMonthlyMapper.class);
    private final FeeLedgerItemMapper ledgerItemMapper = mock(FeeLedgerItemMapper.class);
    private final FeeItemMapper feeItemMapper = mock(FeeItemMapper.class);
    private final FeeLedgerGuard guard = mock(FeeLedgerGuard.class);
    private final FeeLedgerService ledgerService = mock(FeeLedgerService.class);
    private final FeeMonthlyServiceImpl service = new FeeMonthlyServiceImpl(
        monthlyMapper, ledgerItemMapper, feeItemMapper, guard, ledgerService);

    @Test
    void revertRestoresDefaultFactorAndAutoAmount() {
        FeeMonthlyEntity reverted = row(false);
        reverted.setMonthFactor(new BigDecimal("0.50"));
        reverted.setOccurrenceAmount(new BigDecimal("750.00"));
        when(guard.requireItem(ITEM_ID)).thenReturn(item());
        when(monthlyMapper.selectOne(any())).thenReturn(row(true));
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
