package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeMonthlyEntity;
import com.cornerstone.module.education.fee.domain.entity.FeePaymentEntity;
import com.cornerstone.module.education.fee.dto.MiniappStudentFeeView;
import com.cornerstone.module.education.fee.mapper.FeeItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerMapper;
import com.cornerstone.module.education.fee.mapper.FeeMonthlyMapper;
import com.cornerstone.module.education.fee.mapper.FeePaymentMapper;
import com.cornerstone.module.education.fee.service.impl.StudentFeeQueryServiceImpl;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.TermMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 家长端费用概览：金额必须与后台台账同一套公式，只剔除凭证号与内部备注。
 */
class StudentFeeQueryServiceImplTest {

    private final FeeLedgerMapper feeLedgerMapper = mock(FeeLedgerMapper.class);
    private final FeeLedgerItemMapper feeLedgerItemMapper = mock(FeeLedgerItemMapper.class);
    private final FeeMonthlyMapper feeMonthlyMapper = mock(FeeMonthlyMapper.class);
    private final FeePaymentMapper feePaymentMapper = mock(FeePaymentMapper.class);
    private final FeeItemMapper feeItemMapper = mock(FeeItemMapper.class);
    private final TeachingClassMapper teachingClassMapper = mock(TeachingClassMapper.class);
    private final TermMapper termMapper = mock(TermMapper.class);

    private StudentFeeQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentFeeQueryServiceImpl(feeLedgerMapper, feeLedgerItemMapper, feeMonthlyMapper,
            feePaymentMapper, feeItemMapper, teachingClassMapper, termMapper);
    }

    @Test
    void emptyStudentReturnsZeroTotals() {
        when(feeLedgerMapper.selectList(any())).thenReturn(List.of());

        MiniappStudentFeeView view = service.studentFees(10L);

        assertEquals(10L, view.studentId());
        assertEquals(new BigDecimal("0.0000"), view.receivable());
        assertEquals(new BigDecimal("0.0000"), view.paid());
        assertEquals(new BigDecimal("0.0000"), view.unpaid());
        assertEquals(new BigDecimal("0.0000"), view.balance());
        assertTrue(view.ledgers().isEmpty());
        assertTrue(view.payments().isEmpty());
    }

    @Test
    void aggregatesLedgerItemsAndPaymentsWithSharedEngine() {
        FeeLedgerEntity ledger = ledger(100L, 20L, 30L);
        when(feeLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        when(feeLedgerItemMapper.selectList(any())).thenReturn(List.of(item(200L, 40L)));
        when(feeMonthlyMapper.selectList(any())).thenReturn(List.of(monthly(200L)));
        when(feePaymentMapper.selectList(any())).thenReturn(List.of(payment(200L, "2000")));
        when(feeItemMapper.selectBatchIds(any())).thenReturn(List.of(feeItem(40L)));
        when(teachingClassMapper.selectBatchIds(any())).thenReturn(List.of(clazz(20L)));
        when(termMapper.selectBatchIds(any())).thenReturn(List.of(term(30L)));

        MiniappStudentFeeView view = service.studentFees(10L);

        // 应收 = 2 个月 × 1500；实收 2000；结余 = 实收 2000 − 发生 1500
        assertEquals(new BigDecimal("3000.0000"), view.receivable());
        assertEquals(new BigDecimal("2000.0000"), view.paid());
        assertEquals(new BigDecimal("1000.0000"), view.unpaid());
        assertEquals(new BigDecimal("500.0000"), view.balance());

        assertEquals(1, view.ledgers().size());
        assertEquals("三(1)班", view.ledgers().get(0).className());
        assertEquals("2026秋", view.ledgers().get(0).termName());
        assertEquals("保教费", view.ledgers().get(0).items().get(0).itemName());

        assertEquals(1, view.payments().size());
        assertEquals(new BigDecimal("2000"), view.payments().get(0).amount());
        assertEquals("wechat", view.payments().get(0).paymentMethod());
    }

    private FeeLedgerEntity ledger(Long id, Long classId, Long termId) {
        FeeLedgerEntity ledger = new FeeLedgerEntity();
        ledger.setId(id);
        ledger.setInstitutionId(1L);
        ledger.setStudentId(10L);
        ledger.setClassId(classId);
        ledger.setTermId(termId);
        ledger.setStatus("active");
        return ledger;
    }

    private FeeLedgerItemEntity item(Long id, Long feeItemId) {
        FeeLedgerItemEntity item = new FeeLedgerItemEntity();
        item.setId(id);
        item.setLedgerId(100L);
        item.setFeeItemId(feeItemId);
        item.setBillingMode("monthly");
        item.setBillableMonths(new BigDecimal("2"));
        item.setMonthlyStandard(new BigDecimal("1500"));
        item.setOpeningBalance(BigDecimal.ZERO);
        item.setOtherDeduction(BigDecimal.ZERO);
        return item;
    }

    private FeeMonthlyEntity monthly(Long ledgerItemId) {
        FeeMonthlyEntity monthly = new FeeMonthlyEntity();
        monthly.setId(400L);
        monthly.setLedgerId(100L);
        monthly.setLedgerItemId(ledgerItemId);
        monthly.setBillingMonth(LocalDate.of(2026, 9, 1));
        monthly.setOccurrenceAmount(new BigDecimal("1500"));
        return monthly;
    }

    private FeePaymentEntity payment(Long ledgerItemId, String amount) {
        FeePaymentEntity payment = new FeePaymentEntity();
        payment.setId(300L);
        payment.setLedgerId(100L);
        payment.setLedgerItemId(ledgerItemId);
        payment.setPaymentNo("SK-20260901-001");
        payment.setAmount(new BigDecimal(amount));
        payment.setPaidOn(LocalDate.of(2026, 9, 1));
        payment.setPaymentMethod("wechat");
        return payment;
    }

    private FeeItemEntity feeItem(Long id) {
        FeeItemEntity feeItem = new FeeItemEntity();
        feeItem.setId(id);
        feeItem.setItemName("保教费");
        feeItem.setBillingMode("monthly");
        feeItem.setSortNo(10);
        return feeItem;
    }

    private TeachingClassEntity clazz(Long id) {
        TeachingClassEntity clazz = new TeachingClassEntity();
        clazz.setId(id);
        clazz.setClassName("三(1)班");
        return clazz;
    }

    private TermEntity term(Long id) {
        TermEntity term = new TermEntity();
        term.setId(id);
        term.setTermName("2026秋");
        return term;
    }
}
