package com.cornerstone.module.education.fee.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "收款登记请求")
public record FeePaymentCreateRequest(
    @Schema(description = "台账ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("ledger_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "收费台账不能为空")
    Long ledgerId,
    @Schema(description = "台账明细ID(收到的钱归到哪个收费项目)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("ledger_item_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "收费项目不能为空")
    Long ledgerItemId,
    @Schema(description = "收款单号(留空自动生成 SK-日期-序号)")
    @JsonAlias("payment_no")
    @Size(max = 64, message = "收款单号不能超过 64 个字符")
    String paymentNo,
    @Schema(description = "收款金额(正数;负数=冲正/退回)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "收款金额不能为空")
    @Digits(integer = 12, fraction = 4, message = "收款金额最多 12 位整数、4 位小数")
    BigDecimal amount,
    @Schema(description = "收款日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("paid_on")
    @NotNull(message = "收款日期不能为空")
    LocalDate paidOn,
    @Schema(description = "收款方式字典(fee_payment_method:cash=现金,wechat=微信,alipay=支付宝,bank=银行转账,other=其他)",
            allowableValues = {"cash", "wechat", "alipay", "bank", "other"})
    @JsonAlias("payment_method")
    String paymentMethod,
    @Schema(description = "归属账期(当月1日,可空)")
    @JsonAlias("billing_month")
    LocalDate billingMonth,
    @Schema(description = "凭证号/银行流水号")
    @JsonAlias("voucher_no")
    @Size(max = 64, message = "凭证号不能超过 64 个字符")
    String voucherNo,
    @Schema(description = "备注(如 按月收)")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    String remark
) {
}
