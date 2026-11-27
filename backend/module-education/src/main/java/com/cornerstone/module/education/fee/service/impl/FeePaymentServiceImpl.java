package com.cornerstone.module.education.fee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeePaymentEntity;
import com.cornerstone.module.education.fee.dto.FeePaymentCreateRequest;
import com.cornerstone.module.education.fee.dto.FeePaymentUpdateRequest;
import com.cornerstone.module.education.fee.dto.FeePaymentView;
import com.cornerstone.module.education.fee.mapper.FeeLedgerItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerMapper;
import com.cornerstone.module.education.fee.mapper.FeePaymentMapper;
import com.cornerstone.module.education.fee.service.FeePaymentService;
import com.cornerstone.module.education.fee.support.FeeBillingEngine;
import com.cornerstone.module.education.support.DictValueValidator;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import com.cornerstone.module.organization.service.support.InstitutionScopeGuard;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class FeePaymentServiceImpl implements FeePaymentService {

    /** 收款单号的日期段用 BASIC_ISO_DATE：连续八位数字，不带分隔符 */
    private static final DateTimeFormatter PAYMENT_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** 自动单号重试次数：并发登记撞号时换下一个序号，不把冲突抛给操作员 */
    private static final int MAX_PAYMENT_NO_RETRY = 5;

    private final FeePaymentMapper feePaymentMapper;
    private final FeeLedgerMapper feeLedgerMapper;
    private final FeeLedgerItemMapper feeLedgerItemMapper;
    private final DictValueValidator dictValueValidator;

    public FeePaymentServiceImpl(FeePaymentMapper feePaymentMapper, FeeLedgerMapper feeLedgerMapper,
                                 FeeLedgerItemMapper feeLedgerItemMapper,
                                 DictValueValidator dictValueValidator) {
        this.feePaymentMapper = feePaymentMapper;
        this.feeLedgerMapper = feeLedgerMapper;
        this.feeLedgerItemMapper = feeLedgerItemMapper;
        this.dictValueValidator = dictValueValidator;
    }

    @Override
    public List<FeePaymentView> listPayments(Long ledgerId, Long ledgerItemId) {
        if (ledgerId == null && ledgerItemId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请指定收费台账或收费项目");
        }
        Long resolvedLedgerId = ledgerId;
        if (ledgerItemId != null) {
            FeeLedgerItemEntity item = requireItem(ledgerItemId);
            if (resolvedLedgerId != null && !Objects.equals(item.getLedgerId(), resolvedLedgerId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "收费项目不属于该台账");
            }
            resolvedLedgerId = item.getLedgerId();
        }
        requireLedger(resolvedLedgerId);
        LambdaQueryWrapper<FeePaymentEntity> wrapper = new LambdaQueryWrapper<FeePaymentEntity>()
            .eq(FeePaymentEntity::getLedgerId, resolvedLedgerId);
        if (ledgerItemId != null) {
            wrapper.eq(FeePaymentEntity::getLedgerItemId, ledgerItemId);
        }
        wrapper.orderByDesc(FeePaymentEntity::getPaidOn).orderByDesc(FeePaymentEntity::getId);
        return feePaymentMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    @Override
    public FeePaymentView createPayment(FeePaymentCreateRequest request) {
        FeeLedgerEntity ledger = requireLedger(request.ledgerId());
        FeeLedgerItemEntity item = requireItem(request.ledgerItemId());
        if (!Objects.equals(item.getLedgerId(), ledger.getId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "收费项目不属于该台账");
        }
        FeePaymentEntity entity = new FeePaymentEntity();
        entity.setInstitutionId(ledger.getInstitutionId());
        entity.setCampusId(ledger.getCampusId());
        entity.setLedgerId(ledger.getId());
        entity.setLedgerItemId(item.getId());
        entity.setAmount(FeeBillingEngine.money(request.amount()));
        entity.setPaidOn(request.paidOn());
        entity.setPaymentMethod(paymentMethod(request.paymentMethod()));
        entity.setBillingMonth(normalizeMonth(request.billingMonth()));
        entity.setVoucherNo(normalizeToNull(request.voucherNo()));
        entity.setRemark(normalizeToNull(request.remark()));
        entity.setDeleted(Boolean.FALSE);

        String manualNo = normalizeToNull(request.paymentNo());
        if (manualNo != null) {
            entity.setPaymentNo(manualNo);
            insert(entity);
        } else {
            insertWithGeneratedNo(entity);
        }
        return toView(entity);
    }

    @Override
    public FeePaymentView updatePayment(Long id, FeePaymentUpdateRequest request) {
        FeePaymentEntity existing = feePaymentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收款流水不存在：" + id);
        }
        requireLedger(existing.getLedgerId());
        if (request.amount() != null) {
            existing.setAmount(FeeBillingEngine.money(request.amount()));
        }
        if (request.paidOn() != null) {
            existing.setPaidOn(request.paidOn());
        }
        if (request.paymentMethod() != null) {
            existing.setPaymentMethod(paymentMethod(request.paymentMethod()));
        }
        if (request.billingMonth() != null) {
            existing.setBillingMonth(normalizeMonth(request.billingMonth()));
        }
        if (request.voucherNo() != null) {
            existing.setVoucherNo(normalizeToNull(request.voucherNo()));
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        feePaymentMapper.updateById(existing);
        return toView(existing);
    }

    @Override
    public void deletePayment(Long id) {
        FeePaymentEntity existing = feePaymentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收款流水不存在：" + id);
        }
        requireLedger(existing.getLedgerId());
        // 收款是财务凭证，逻辑删除留痕；实收由子表现算，删掉即从台账消失
        feePaymentMapper.deleteById(id);
    }

    private FeeLedgerEntity requireLedger(Long ledgerId) {
        FeeLedgerEntity ledger = feeLedgerMapper.selectById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收费台账不存在：" + ledgerId);
        }
        InstitutionScopeGuard.assertOwn(ledger.getInstitutionId(), "收费台账");
        CampusScopeGuard.assertCampus(ledger.getCampusId(), "收费台账");
        return ledger;
    }

    private FeeLedgerItemEntity requireItem(Long ledgerItemId) {
        FeeLedgerItemEntity item = feeLedgerItemMapper.selectById(ledgerItemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收费项目行不存在：" + ledgerItemId);
        }
        return item;
    }

    private void insert(FeePaymentEntity entity) {
        try {
            feePaymentMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "收款单号已存在：" + entity.getPaymentNo());
        }
    }

    private void insertWithGeneratedNo(FeePaymentEntity entity) {
        for (int attempt = 0; attempt < MAX_PAYMENT_NO_RETRY; attempt++) {
            entity.setPaymentNo(nextPaymentNo(entity.getInstitutionId(), entity.getPaidOn(), attempt));
            try {
                feePaymentMapper.insert(entity);
                return;
            } catch (DuplicateKeyException exception) {
                // 并发撞号：换下一个序号重试
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "收款单号生成冲突，请稍后重试或手工填写单号");
    }

    /** 单号格式 SK-日期-序号；序号取机构当天已用最大号 + 1（含软删墓碑，删掉的号不回收）。 */
    private String nextPaymentNo(Long institutionId, java.time.LocalDate paidOn, int offset) {
        String prefix = "SK-" + paidOn.format(PAYMENT_NO_DATE) + "-";
        long used = feePaymentMapper.maxPaymentNoSequence(institutionId, prefix);
        return prefix + String.format("%04d", used + 1 + offset);
    }

    private String paymentMethod(String value) {
        String validated = dictValueValidator.validate("fee_payment_method", "收款方式", value);
        return validated == null ? "cash" : validated;
    }

    private java.time.LocalDate normalizeMonth(java.time.LocalDate month) {
        return month == null ? null : month.withDayOfMonth(1);
    }

    private FeePaymentView toView(FeePaymentEntity entity) {
        return new FeePaymentView(entity.getId(), entity.getLedgerItemId(), entity.getPaymentNo(),
            entity.getAmount(), entity.getPaidOn(), entity.getPaymentMethod(), entity.getBillingMonth(),
            entity.getVoucherNo(), entity.getRemark());
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
