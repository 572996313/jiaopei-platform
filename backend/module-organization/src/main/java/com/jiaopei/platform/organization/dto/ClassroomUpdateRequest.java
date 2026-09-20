package com.jiaopei.platform.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import java.math.BigDecimal;

public record ClassroomUpdateRequest(
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @JsonAlias({"code", "classroom_code"})
    String classroomCode,
    @JsonAlias({"name", "classroom_name"})
    String classroomName,
    @JsonAlias("room_type")
    String roomType,
    @JsonAlias({"floor", "floor_no"})
    String floorNo,
    @JsonAlias({"capacity", "seat_count", "seat_capacity"})
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