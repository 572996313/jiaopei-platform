package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "收款流水行")
public record FeePaymentView(
    @Schema(description = "流水ID") Long id,
    @Schema(description = "台账明细ID") Long ledgerItemId,
    @Schema(description = "收款单号") String paymentNo,
    @Schema(description = "收款金额") BigDecimal amount,
    @Schema(description = "收款日期") LocalDate paidOn,
    @Schema(description = "收款方式字典值") String paymentMethod,
    @Schema(description = "归属账期") LocalDate billingMonth,
    @Schema(description = "凭证号") String voucherNo,
    @Schema(description = "备注") String remark
) {
}
