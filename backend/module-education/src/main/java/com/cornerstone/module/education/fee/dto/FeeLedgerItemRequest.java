package com.cornerstone.module.education.fee.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 台账里的一行收费项目。标准留空时按价目表取价；取不到就记 0 并在页面上提示，
 * 不拦截建账——现场常见"先建账后补价目"。
 */
@Schema(description = "台账收费项目行")
public record FeeLedgerItemRequest(
    @Schema(description = "收费项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("fee_item_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "收费项目不能为空")
    Long feeItemId,
    @Schema(description = "收费月数(留空按学期默认月数)")
    @JsonAlias("billable_months")
    BigDecimal billableMonths,
    @Schema(description = "月标准(留空按价目表;显式传 0 表示免费)")
    @JsonAlias("monthly_standard")
    BigDecimal monthlyStandard,
    @Schema(description = "日标准(留空按价目表;显式传 0 表示免费)")
    @JsonAlias("daily_rate")
    BigDecimal dailyRate,
    @Schema(description = "上期结余(正=预收,负=欠费)")
    @JsonAlias("opening_balance")
    BigDecimal openingBalance,
    @Schema(description = "其他抵扣")
    @JsonAlias("other_deduction")
    BigDecimal otherDeduction,
    @Schema(description = "备注")
    String remark
) {
}
