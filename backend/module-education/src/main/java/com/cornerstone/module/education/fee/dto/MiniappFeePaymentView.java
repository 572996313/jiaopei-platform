package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "家长端收款流水(不含凭证号与内部备注)")
public record MiniappFeePaymentView(
    @Schema(description = "流水ID")
    Long id,

    @Schema(description = "收款单号")
    String paymentNo,

    @Schema(description = "收款金额(负数=冲正/退回)")
    BigDecimal amount,

    @Schema(description = "收款日期")
    LocalDate paidOn,

    @Schema(description = "收款方式字典(fee_payment_method:cash=现金,wechat=微信,alipay=支付宝,bank=银行转账,other=其他)")
    String paymentMethod,

    @Schema(description = "归属账期(当月1日,可空)")
    LocalDate billingMonth
) {
}
