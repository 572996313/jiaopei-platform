package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "排课课次视图")
public record ScheduleLessonView(
    @Schema(description = "课次ID")
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
    @Schema(description = "时段ID")
    Long timeSlotId,
    @Schema(description = "来源排课计划ID(手工排课为空)")
    Long planId,
    @Schema(description = "上课日期")
    LocalDate lessonDate,
    @Schema(description = "星期(1=周一,7=周日)")
    Integer lessonWeekday,
    @Schema(description = "开始时间")
    LocalTime startTime,
    @Schema(description = "结束时间")
    LocalTime endTime,
    @Schema(description = "课次标题")
    String lessonTitle,
    @Schema(description = "课次状态(scheduled=已排课,rescheduled=已调课,cancelled=已停课,makeup=已补课,completed=已结课)", allowableValues = {"scheduled", "rescheduled", "cancelled", "makeup", "completed"})
    String lessonStatus,
    @Schema(description = "变更类型(cancelled_makeup=停课补课)", allowableValues = {"cancelled_makeup"})
    String changeType,
    @Schema(description = "关联课次ID")
    Long relatedLessonId,
    @Schema(description = "是否有待处理的补课")
    Boolean pendingMakeup,
    @Schema(description = "授课教师ID列表")
    List<Long> teacherIds,
    @Schema(description = "备注")
    String remark
) {
}
