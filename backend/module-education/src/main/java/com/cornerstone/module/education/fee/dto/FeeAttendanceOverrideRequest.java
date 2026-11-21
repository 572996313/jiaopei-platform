package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "月度出勤天数手工覆盖请求")
public record FeeAttendanceOverrideRequest(
    @Schema(description = "生效出勤天数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "出勤天数不能为空")
    @DecimalMin(value = "0.00", message = "出勤天数不能为负数")
    @Digits(integer = 4, fraction = 4, message = "出勤天数最多 4 位整数、4 位小数")
    BigDecimal attendanceDays,
    @Schema(description = "覆盖原因")
    @Size(max = 200, message = "覆盖原因不能超过 200 个字符")
    String overrideReason
) {
}
