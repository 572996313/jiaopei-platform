package com.cornerstone.module.education;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.fee.domain.entity.FeeAttendanceMonthEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.dto.FeeAttendanceView;
import com.cornerstone.module.education.fee.dto.FeeRecalculateRequest;
import com.cornerstone.module.education.fee.mapper.FeeAttendanceMonthMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerMapper;
import com.cornerstone.module.education.fee.service.FeeLedgerService;
import com.cornerstone.module.education.fee.service.impl.FeeAttendanceServiceImpl;
import com.cornerstone.module.education.fee.support.FeeLedgerGuard;
import com.cornerstone.module.organization.mapper.TermMapper;
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
 * 出勤覆盖撤销：手工值回到考勤汇总值，并且条件更新只对仍处于手工覆盖的行生效。
 */
class FeeAttendanceRevertTest {

    private static final Long LEDGER_ID = 700L;
    private static final LocalDate MONTH = LocalDate.of(2026, 8, 1);

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FeeAttendanceMonthEntity.class);
    }

    private final FeeAttendanceMonthMapper attendanceMapper = mock(FeeAttendanceMonthMapper.class);
    private final FeeLedgerMapper ledgerMapper = mock(FeeLedgerMapper.class);
    private final TermMapper termMapper = mock(TermMapper.class);
    private final FeeLedgerGuard guard = mock(FeeLedgerGuard.class);
    private final FeeLedgerService ledgerService = mock(FeeLedgerService.class);
    private final FeeAttendanceServiceImpl service = new FeeAttendanceServiceImpl(
        attendanceMapper, ledgerMapper, termMapper, guard, ledgerService);

    @Test
    void revertRestoresAutoDaysAndClearsOverride() {
        FeeAttendanceMonthEntity row = row(1L, true);
        FeeAttendanceMonthEntity reverted = row(1L, false);
        reverted.setAttendanceDays(new BigDecimal("18.00"));
        reverted.setSource("AUTO");
        when(guard.requireLedger(LEDGER_ID)).thenReturn(new FeeLedgerEntity());
        when(attendanceMapper.selectOne(any())).thenReturn(row);
        when(attendanceMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(attendanceMapper.selectById(1L)).thenReturn(reverted);

        FeeAttendanceView view = service.revert(LEDGER_ID, MONTH);

        assertEquals(new BigDecimal("18.00"), view.attendanceDays());
        assertEquals("AUTO", view.source());
        assertFalse(view.manualOverride());
        verify(ledgerService).recalculate(any(FeeRecalculateRequest.class));
    }

    @Test
    void revertRejectsWhenMonthHasNoAttendanceRow() {
        when(guard.requireLedger(LEDGER_ID)).thenReturn(new FeeLedgerEntity());
        when(attendanceMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.revert(LEDGER_ID, MONTH));

        assertEquals(ResultCode.NOT_FOUND, exception.getResultCode());
        verify(attendanceMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void revertRejectsWhenRowIsNotManuallyOverridden() {
        when(guard.requireLedger(LEDGER_ID)).thenReturn(new FeeLedgerEntity());
        when(attendanceMapper.selectOne(any())).thenReturn(row(1L, false));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.revert(LEDGER_ID, MONTH));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        verify(attendanceMapper, never()).update(any(), any(Wrapper.class));
        verify(ledgerService, never()).recalculate(any());
    }

    @Test
    void revertFailsWhenConcurrentChangeAlreadyClearedOverride() {
        when(guard.requireLedger(LEDGER_ID)).thenReturn(new FeeLedgerEntity());
        when(attendanceMapper.selectOne(any())).thenReturn(row(1L, true));
        when(attendanceMapper.update(any(), any(Wrapper.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.revert(LEDGER_ID, MONTH));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        verify(ledgerService, never()).recalculate(any());
    }

    private FeeAttendanceMonthEntity row(Long id, boolean manual) {
        FeeAttendanceMonthEntity row = new FeeAttendanceMonthEntity();
        row.setId(id);
        row.setLedgerId(LEDGER_ID);
        row.setBillingMonth(MONTH);
        row.setAttendanceDays(new BigDecimal("20.00"));
        row.setAutoDays(new BigDecimal("18.00"));
        row.setManualOverride(manual);
        row.setSource(manual ? "MANUAL" : "AUTO");
        return row;
    }
}
