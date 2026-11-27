package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "排课计划视图")
public record SchedulePlanView(
    @Schema(description = "计划ID")
    Long id,
    @Schema(description = "机构ID")
    Long institutionId,
    @Schema(description = "校区ID")
    Long campusId,
    @Schema(description = "学期ID")
    Long termId,
    @Schema(description = "班级ID")
    Long classId,
    @Schema(description = "教室ID")
    Long classroomId,
    @Schema(description = "授课教师ID列表")
    List<Long> teacherIds,
    @Schema(description = "每周节次模式")
    List<ScheduleSlotCell> slots,
    @Schema(description = "排课频率(every_week=每周,odd_week=单周,even_week=双周)", allowableValues = {"every_week", "odd_week", "even_week"})
    String weekFrequency,
    @Schema(description = "生效起始日期")
    LocalDate startDate,
    @Schema(description = "生效截止日期")
    LocalDate endDate,
    @Schema(description = "跳过日期列表")
    List<LocalDate> skipDates,
    @Schema(description = "计划状态(active=启用,inactive=停用)", allowableValues = {"active", "inactive"})
    String status,
    @Schema(description = "备注")
    String remark,
    @Schema(description = "创建时间")
    LocalDateTime createdAt
) {
}
