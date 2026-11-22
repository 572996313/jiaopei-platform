package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "家长端学员费用概览(只读,金额由服务端现算)")
public record MiniappStudentFeeView(
    @Schema(description = "学员ID")
    Long studentId,

    @Schema(description = "应收合计")
    BigDecimal receivable,

    @Schema(description = "实收合计")
    BigDecimal paid,

    @Schema(description = "未收合计(负数=预收/多收)")
    BigDecimal unpaid,

    @Schema(description = "结余(正数=可退金额)")
    BigDecimal balance,

    @Schema(description = "收费台账列表(新账在前)")
    List<MiniappFeeLedgerView> ledgers,

    @Schema(description = "收款流水(收款日期倒序,最多50条)")
    List<MiniappFeePaymentView> payments
) {
}
