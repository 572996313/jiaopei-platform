package com.jiaopei.platform.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import java.time.LocalTime;

public record TimeSlotUpdateRequest(
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @JsonAlias({"code", "slot_code"})
    String slotCode,
    @JsonAlias({"name", "slot_name"})
    String slotName,
    Integer weekday,
    LocalTime startTime,
    LocalTime endTime,
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