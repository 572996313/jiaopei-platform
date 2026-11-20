package com.cornerstone.module.education.fee.service;

import com.cornerstone.module.education.fee.dto.FeePaymentCreateRequest;
import com.cornerstone.module.education.fee.dto.FeePaymentUpdateRequest;
import com.cornerstone.module.education.fee.dto.FeePaymentView;
import java.util.List;

/** 收款流水登记。 */
public interface FeePaymentService {

    List<FeePaymentView> listPayments(Long ledgerId, Long ledgerItemId);

    FeePaymentView createPayment(FeePaymentCreateRequest request);

    FeePaymentView updatePayment(Long id, FeePaymentUpdateRequest request);

    void deletePayment(Long id);
}
