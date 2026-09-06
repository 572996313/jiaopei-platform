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
    @NotNull(message = "institutionId 不能为空")
    Long institutionId,
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @JsonAlias({"code", "slot_code"})
    @NotBlank(message = "slotCode 不能为空")
    String slotCode,
    @JsonAlias({"name", "slot_name"})
    @NotBlank(message = "slotName 不能为空")
    String slotName,
    @NotNull(message = "weekday 不能为空")
    @Min(value = 1, message = "weekday 必须在 1 到 7 之间")
    @Max(value = 7, message = "weekday 必须在 1 到 7 之间")
    Integer weekday,
    @NotNull(message = "startTime 不能为空")
    LocalTime startTime,
    @NotNull(message = "endTime 不能为空")
    LocalTime endTime,
    @NotNull(message = "durationMinutes 不能为空")
    @Min(value = 1, message = "durationMinutes 必须大于 0")
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
