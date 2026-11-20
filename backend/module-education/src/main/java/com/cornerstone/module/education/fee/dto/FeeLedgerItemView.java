package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "台账明细行(含派生值)")
public record FeeLedgerItemView(
    @Schema(description = "明细ID") Long id,
    @Schema(description = "收费项目ID") Long feeItemId,
    @Schema(description = "收费项目编码") String itemCode,
    @Schema(description = "收费项目名称") String itemName,
    @Schema(description = "计费方式(monthly=按月,daily=按天)") String billingMode,
    @Schema(description = "收费月数") BigDecimal billableMonths,
    @Schema(description = "月标准") BigDecimal monthlyStandard,
    @Schema(description = "日标准") BigDecimal dailyRate,
    @Schema(description = "上期结余") BigDecimal openingBalance,
    @Schema(description = "其他抵扣") BigDecimal otherDeduction,
    @Schema(description = "应收") BigDecimal receivable,
    @Schema(description = "实收") BigDecimal paid,
    @Schema(description = "未收费") BigDecimal unpaid,
    @Schema(description = "合计发生") BigDecimal totalOccurrence,
    @Schema(description = "结余") BigDecimal balance,
    @Schema(description = "取价来源(PRICE/STUDENT/MANUAL)") String priceSource,
    @Schema(description = "备注") String remark
) {
}
