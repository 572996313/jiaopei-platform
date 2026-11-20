package com.cornerstone.module.education.fee.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_fee_payment")
@Schema(description = "收款流水")
public class FeePaymentEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID(冗余)")
    private Long campusId;

    @Schema(description = "台账ID")
    private Long ledgerId;

    @Schema(description = "台账明细ID")
    private Long ledgerItemId;

    @Schema(description = "收款单号")
    private String paymentNo;

    @Schema(description = "收款金额(正数;负数=冲正/退回)")
    private BigDecimal amount;

    @Schema(description = "收款日期")
    private LocalDate paidOn;

    @Schema(description = "收款方式字典(fee_payment_method:cash=现金,wechat=微信,alipay=支付宝,bank=银行转账,other=其他)",
            allowableValues = {"cash", "wechat", "alipay", "bank", "other"})
    private String paymentMethod;

    @Schema(description = "归属账期(当月1日,可空)")
    private LocalDate billingMonth;

    @Schema(description = "凭证号/银行流水号")
    private String voucherNo;

    @Schema(description = "备注(如 按月收)")
    private String remark;
}
