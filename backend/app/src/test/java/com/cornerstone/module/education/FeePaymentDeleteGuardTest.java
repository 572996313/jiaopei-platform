package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeePaymentEntity;
import com.cornerstone.module.education.fee.dto.FeePaymentCreateRequest;
import com.cornerstone.module.education.fee.mapper.FeeAttendanceMonthMapper;
import com.cornerstone.module.education.fee.mapper.FeeItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerMapper;
import com.cornerstone.module.education.fee.mapper.FeeMonthlyMapper;
import com.cornerstone.module.education.fee.mapper.FeePaymentMapper;
import com.cornerstone.module.education.fee.service.impl.FeeLedgerServiceImpl;
import com.cornerstone.module.education.fee.service.impl.FeePaymentServiceImpl;
import com.cornerstone.module.education.fee.support.FeeLedgerGuard;
import com.cornerstone.module.education.fee.support.FeePriceResolver;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.StudentParentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.support.DictValueValidator;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 收款单号在「删除 → 重建同号 → 再删」下的防线。
 *
 * <p>2026-11-21 现场事故：删除已删过 -0002 后再删除一张复用 -0002 的活流水时，
 * uk_jp_fee_payment_no (机构, 单号, deleted) 只容一条墓碑，UPDATE deleted = 1 直接 1062。
 * 修复两层：V108 把唯一键换成守卫列（墓碑为 NULL）；取号改为连墓碑取最大序号，单号不回收。</p>
 */
@ExtendWith(MockitoExtension.class)
class FeePaymentDeleteGuardTest {

    private static final Long INSTITUTION_ID = 100L;
    private static final Long LEDGER_ID = 700L;
    private static final Long ITEM_ID = 600L;
    private static final Long CAMPUS_ID = 200L;
    private static final LocalDate PAID_ON = LocalDate.of(2026, 11, 21);

    @Mock
    private FeePaymentMapper feePaymentMapper;
    @Mock
    private FeeLedgerMapper feeLedgerMapper;
    @Mock
    private FeeLedgerItemMapper feeLedgerItemMapper;
    @Mock
    private FeeMonthlyMapper feeMonthlyMapper;
    @Mock
    private FeeAttendanceMonthMapper feeAttendanceMonthMapper;
    @Mock
    private FeeItemMapper feeItemMapper;
    @Mock
    private FeePriceResolver feePriceResolver;
    @Mock
    private FeeLedgerGuard feeLedgerGuard;
    @Mock
    private ClassStudentMapper classStudentMapper;
    @Mock
    private TeachingClassMapper teachingClassMapper;
    @Mock
    private TermMapper termMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private StudentParentMapper studentParentMapper;
    @Mock
    private CampusMapper campusMapper;
    @Mock
    private DictValueValidator dictValueValidator;

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FeePaymentEntity.class);
    }

    @Test
    void generatedNoSkipsRetiredNumbers() {
        when(feeLedgerMapper.selectById(LEDGER_ID)).thenReturn(ledger());
        when(feeLedgerItemMapper.selectById(ITEM_ID)).thenReturn(ledgerItem());
        // 当天 SK-20261121-0001 / -0002 都已是墓碑：取号器必须接着发 0003，不能回收旧号
        when(feePaymentMapper.maxPaymentNoSequence(INSTITUTION_ID, "SK-20261121-")).thenReturn(2L);

        new FeePaymentServiceImpl(feePaymentMapper, feeLedgerMapper, feeLedgerItemMapper, dictValueValidator)
            .createPayment(new FeePaymentCreateRequest(LEDGER_ID, ITEM_ID, null,
                new BigDecimal("3000.00"), PAID_ON, null, null, null, null));

        ArgumentCaptor<FeePaymentEntity> captor = ArgumentCaptor.forClass(FeePaymentEntity.class);
        verify(feePaymentMapper).insert(captor.capture());
        assertEquals("SK-20261121-0003", captor.getValue().getPaymentNo());
    }

    @Test
    void deleteLedgerClearsPaymentTombstonesBeforePhysicalDelete() {
        when(feeLedgerGuard.requireLedger(LEDGER_ID)).thenReturn(ledger());
        when(feePaymentMapper.selectCount(any())).thenReturn(0L);

        ledgerService().deleteLedger(LEDGER_ID);

        // 收款墓碑仍占着外键，父行物理删之前必须只清墓碑
        verify(feePaymentMapper).deleteTombstonesByLedgerId(LEDGER_ID);
        verify(feeLedgerMapper).physicalDeleteById(LEDGER_ID);
    }

    @Test
    void deleteLedgerRejectsLivePaymentsWithoutTouchingTombstones() {
        when(feeLedgerGuard.requireLedger(LEDGER_ID)).thenReturn(ledger());
        when(feePaymentMapper.selectCount(any())).thenReturn(1L);

        assertThrows(com.cornerstone.core.common.exception.BusinessException.class,
            () -> ledgerService().deleteLedger(LEDGER_ID));

        verify(feePaymentMapper, never()).deleteTombstonesByLedgerId(LEDGER_ID);
        verify(feeLedgerMapper, never()).physicalDeleteById(LEDGER_ID);
    }

    private FeeLedgerServiceImpl ledgerService() {
        return new FeeLedgerServiceImpl(feeLedgerMapper, feeLedgerItemMapper, feeMonthlyMapper,
            feeAttendanceMonthMapper, feePaymentMapper, feeItemMapper, feePriceResolver, feeLedgerGuard,
            classStudentMapper, teachingClassMapper, termMapper, studentMapper, studentParentMapper,
            campusMapper);
    }

    private FeeLedgerEntity ledger() {
        FeeLedgerEntity ledger = new FeeLedgerEntity();
        ledger.setId(LEDGER_ID);
        ledger.setInstitutionId(INSTITUTION_ID);
        ledger.setCampusId(CAMPUS_ID);
        ledger.setStatus("active");
        return ledger;
    }

    private FeeLedgerItemEntity ledgerItem() {
        FeeLedgerItemEntity item = new FeeLedgerItemEntity();
        item.setId(ITEM_ID);
        item.setLedgerId(LEDGER_ID);
        item.setInstitutionId(INSTITUTION_ID);
        item.setCampusId(CAMPUS_ID);
        return item;
    }
}
