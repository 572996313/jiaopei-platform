package com.jiaopei.platform.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ClassroomCreateRequest(
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "campusId 不能为空")
    Long campusId,
    @JsonAlias({"code", "classroom_code"})
    @NotBlank(message = "classroomCode 不能为空")
    String classroomCode,
    @JsonAlias({"name", "classroom_name"})
    @NotBlank(message = "classroomName 不能为空")
    String classroomName,
    @JsonAlias("room_type")
    String roomType,
    @JsonAlias({"floor", "floor_no"})
    String floorNo,
    @JsonAlias({"capacity", "seat_count", "seat_capacity"})
    @NotNull(message = "seatCapacity 不能为空")
    @Min(value = 0, message = "seatCapacity 必须大于或等于 0")
    Integer seatCapacity,
    @JsonAlias("area_sqm")
    BigDecimal areaSqm,
    @JsonAlias("equipment_tags")
    String equipmentTags,
    String status,
    @JsonAlias("allow_scheduling")
    Boolean allowScheduling,
    String remark
) {
}
