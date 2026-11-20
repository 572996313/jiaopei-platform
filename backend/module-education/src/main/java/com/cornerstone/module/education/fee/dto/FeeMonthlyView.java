package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "逐月发生额行")
public record FeeMonthlyView(
    @Schema(description = "行ID") Long id,
    @Schema(description = "台账明细ID") Long ledgerItemId,
    @Schema(description = "收费项目ID") Long feeItemId,
    @Schema(description = "收费项目名称") String itemName,
    @Schema(description = "计费方式") String billingMode,
    @Schema(description = "账期(当月1日)") LocalDate billingMonth,
    @Schema(description = "月数系数") BigDecimal monthFactor,
    @Schema(description = "引擎自动金额") BigDecimal autoAmount,
    @Schema(description = "最终发生额") BigDecimal occurrenceAmount,
    @Schema(description = "是否手工覆盖") boolean manualOverride,
    @Schema(description = "备注") String remark
) {
}
