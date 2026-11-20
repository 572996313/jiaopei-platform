package com.cornerstone.module.education.fee.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "收费价目更新请求")
public record FeePriceUpdateRequest(
    @Schema(description = "月标准")
    @JsonAlias("monthly_price")
    BigDecimal monthlyPrice,
    @Schema(description = "日标准")
    @JsonAlias("daily_price")
    BigDecimal dailyPrice,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
