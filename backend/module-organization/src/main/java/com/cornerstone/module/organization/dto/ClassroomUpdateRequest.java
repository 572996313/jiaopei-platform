package com.cornerstone.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record ClassroomUpdateRequest(
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @JsonAlias({"code", "classroom_code"})
    String classroomCode,
    @JsonAlias({"name", "classroom_name"})
    String classroomName,
    @Schema(description = "教室类型字典(room_type:normal=标准教室,piano=琴房,lab=实验室,dance=舞蹈教室,art=美术教室)", allowableValues = {"normal", "piano", "lab", "dance", "art"})
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