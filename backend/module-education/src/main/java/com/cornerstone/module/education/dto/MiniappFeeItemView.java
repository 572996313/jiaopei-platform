package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "家长端收费项目行(价格与金额均为建账快照)")
public record MiniappFeeItemView(
    @Schema(description = "收费项目名称")
    String itemName,

    @Schema(description = "计费方式(monthly=按月,daily=按天)")
    String billingMode,

    @Schema(description = "收费月数(可为小数)")
    BigDecimal billableMonths,

    @Schema(description = "月标准")
    BigDecimal monthlyStandard,

    @Schema(description = "应收")
    BigDecimal receivable,

    @Schema(description = "实收")
    BigDecimal paid,

    @Schema(description = "未收(负数=预收/多收)")
    BigDecimal unpaid
) {
}
