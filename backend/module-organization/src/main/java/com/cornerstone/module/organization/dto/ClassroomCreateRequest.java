package com.cornerstone.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ClassroomCreateRequest(
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "校区ID不能为空")
    Long campusId,
    @JsonAlias({"code", "classroom_code"})
    @NotBlank(message = "教室编码不能为空")
    String classroomCode,
    @JsonAlias({"name", "classroom_name"})
    @NotBlank(message = "教室名称不能为空")
    String classroomName,
    @Schema(description = "教室类型字典(room_type:normal=标准教室,piano=琴房,lab=实验室,dance=舞蹈教室,art=美术教室)", allowableValues = {"normal", "piano", "lab", "dance", "art"})
    @JsonAlias("room_type")
    String roomType,
    @JsonAlias({"floor", "floor_no"})
    String floorNo,
    @JsonAlias({"capacity", "seat_count", "seat_capacity"})
    @NotNull(message = "座位数不能为空")
    @Min(value = 0, message = "座位数必须大于或等于0")
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
