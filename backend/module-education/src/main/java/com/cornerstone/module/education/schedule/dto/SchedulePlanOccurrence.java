package com.cornerstone.module.education.schedule.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "课次勾选键(日期+时段,与预览条目的定位键一致)")
public record SchedulePlanOccurrence(
    @Schema(description = "上课日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "上课日期不能为空") LocalDate lessonDate,
    @Schema(description = "时段ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "时段ID不能为空")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long timeSlotId
) {
}
