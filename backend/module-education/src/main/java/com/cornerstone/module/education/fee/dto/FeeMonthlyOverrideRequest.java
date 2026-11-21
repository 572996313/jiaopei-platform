package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "逐月发生额手工覆盖请求")
public record FeeMonthlyOverrideRequest(
    @Schema(description = "最终发生额(留空=跟随引擎自动值，只改月份系数时使用)")
    @Digits(integer = 10, fraction = 2, message = "发生额最多 10 位整数、2 位小数")
    BigDecimal occurrenceAmount,
    @Schema(description = "月数系数(可空,留空保持现值)")
    @Digits(integer = 2, fraction = 2, message = "月数系数格式不正确")
    BigDecimal monthFactor,
    @Schema(description = "覆盖原因")
    @Size(max = 200, message = "覆盖原因不能超过 200 个字符")
    String overrideReason
) {
}
