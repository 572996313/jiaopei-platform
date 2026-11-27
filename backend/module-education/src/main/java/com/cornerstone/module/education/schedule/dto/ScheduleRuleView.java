package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "排课规则视图")
public record ScheduleRuleView(
    @Schema(description = "规则ID")
    Long id,
    @Schema(description = "机构ID")
    Long institutionId,
    @Schema(description = "规则类型(forbidden=禁排,required=必排)", allowableValues = {"forbidden", "required"})
    String ruleType,
    @Schema(description = "规则对象类型(class=班级,teacher=教师,classroom=教室)", allowableValues = {"class", "teacher", "classroom"})
    String targetType,
    @Schema(description = "规则对象ID")
    Long targetId,
    @Schema(description = "每周节次格子")
    List<ScheduleSlotCell> slots,
    @Schema(description = "生效起始日期(空=不限)")
    LocalDate startDate,
    @Schema(description = "生效截止日期(空=不限)")
    LocalDate endDate,
    @Schema(description = "规则状态(active=启用,inactive=停用)", allowableValues = {"active", "inactive"})
    String status,
    @Schema(description = "批注")
    String remark,
    @Schema(description = "创建时间")
    LocalDateTime createdAt
) {
}
