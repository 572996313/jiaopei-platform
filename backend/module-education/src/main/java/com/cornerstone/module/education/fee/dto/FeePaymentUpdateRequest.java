package com.cornerstone.module.education.fee.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "收款修改请求")
public record FeePaymentUpdateRequest(
    @Schema(description = "收款金额(正数;负数=冲正/退回)")
    @Digits(integer = 10, fraction = 2, message = "收款金额最多 10 位整数、2 位小数")
    BigDecimal amount,
    @Schema(description = "收款日期")
    @JsonAlias("paid_on")
    LocalDate paidOn,
    @Schema(description = "收款方式字典")
    @JsonAlias("payment_method")
    String paymentMethod,
    @Schema(description = "归属账期(当月1日,可空)")
    @JsonAlias("billing_month")
    LocalDate billingMonth,
    @Schema(description = "凭证号/银行流水号")
    @JsonAlias("voucher_no")
    @Size(max = 64, message = "凭证号不能超过 64 个字符")
    String voucherNo,
    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    String remark
) {
}
