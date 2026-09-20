package com.cornerstone.module.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "模板展开的单条节次(网格编辑器一行单元格)")
public record TimeSlotApplySlot(
    @Schema(description = "星期几(1=周一,7=周日)", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer weekday,
    @Schema(description = "时段编码(如 AM1)", requiredMode = Schema.RequiredMode.REQUIRED)
    String slotCode,
    @Schema(description = "时段名称(如 上1)", requiredMode = Schema.RequiredMode.REQUIRED)
    String slotName,
    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalTime startTime,
    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalTime endTime,
    @Schema(description = "时段类型(morning_study=早自习,teaching=授课,break=课间休息,self_study=晚自习)")
    String slotType,
    @Schema(description = "排序号")
    Integer sortNo
) {
}
