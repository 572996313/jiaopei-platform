package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "排课计划节次模式条目(星期几+时段)")
public record ScheduleSlotCell(
    @Schema(description = "星期(1=周一,7=周日)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "星期不能为空")
    @Min(value = 1, message = "星期取值范围为1-7")
    @Max(value = 7, message = "星期取值范围为1-7") Integer weekday,
    @Schema(description = "时段ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "时段ID不能为空") Long timeSlotId
) {
}
