package com.cornerstone.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record TimeSlotCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @JsonAlias({"code", "slot_code"})
    @NotBlank(message = "时段编码不能为空")
    String slotCode,
    @JsonAlias({"name", "slot_name"})
    @NotBlank(message = "时段名称不能为空")
    String slotName,
    @NotNull(message = "星期不能为空")
    @Min(value = 1, message = "星期必须在1到7之间")
    @Max(value = 7, message = "星期必须在1到7之间")
    Integer weekday,
    @NotNull(message = "开始时间不能为空")
    LocalTime startTime,
    @NotNull(message = "结束时间不能为空")
    LocalTime endTime,
    @NotNull(message = "时长(分钟)不能为空")
    @Min(value = 1, message = "时长(分钟)必须大于0")
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
