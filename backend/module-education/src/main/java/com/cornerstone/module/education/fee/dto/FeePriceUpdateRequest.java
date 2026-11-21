package com.cornerstone.module.education.fee.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

@Schema(description = "收费价目更新请求")
public record FeePriceUpdateRequest(
    @Schema(description = "月标准")
    @JsonAlias("monthly_price")
    @Digits(integer = 10, fraction = 6, message = "月标准最多 10 位整数、6 位小数")
    BigDecimal monthlyPrice,
    @Schema(description = "日标准")
    @JsonAlias("daily_price")
    @Digits(integer = 10, fraction = 6, message = "日标准最多 10 位整数、6 位小数")
    BigDecimal dailyPrice,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
