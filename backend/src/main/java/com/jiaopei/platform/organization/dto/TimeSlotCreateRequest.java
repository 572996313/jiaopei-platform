package com.jiaopei.platform.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record TimeSlotCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @JsonAlias({"code", "slot_code"})
    @NotBlank(message = "slotCode is required")
    String slotCode,
    @JsonAlias({"name", "slot_name"})
    @NotBlank(message = "slotName is required")
    String slotName,
    @NotNull(message = "weekday is required")
    @Min(value = 1, message = "weekday must be between 1 and 7")
    @Max(value = 7, message = "weekday must be between 1 and 7")
    Integer weekday,
    @NotNull(message = "startTime is required")
    LocalTime startTime,
    @NotNull(message = "endTime is required")
    LocalTime endTime,
    @NotNull(message = "durationMinutes is required")
    @Min(value = 1, message = "durationMinutes must be greater than 0")
    @JsonAlias("duration_minutes")
    Integer durationMinutes,
    @JsonAlias("slot_type")
    String slotType,
    @JsonAlias("applicable_scene")
    String applicableScene,
    String status,
    @JsonAlias({"sort", "sort_no"})
    Integer sortNo,
    String remark
) {
}
