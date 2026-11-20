package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "月度出勤行")
public record FeeAttendanceView(
    @Schema(description = "行ID") Long id,
    @Schema(description = "账期(当月1日)") LocalDate billingMonth,
    @Schema(description = "生效出勤天数") BigDecimal attendanceDays,
    @Schema(description = "考勤汇总自动值") BigDecimal autoDays,
    @Schema(description = "是否手工覆盖") boolean manualOverride,
    @Schema(description = "来源(AUTO/MANUAL/IMPORT)") String source,
    @Schema(description = "备注") String remark
) {
}
