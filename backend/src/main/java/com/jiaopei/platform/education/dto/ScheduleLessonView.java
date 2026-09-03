package com.jiaopei.platform.education.dto;

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
    @Schema(description = "课次状态(SCHEDULED/TAKEN/CANCELLED/MAKEUP)", allowableValues = {"SCHEDULED", "TAKEN", "CANCELLED", "MAKEUP"})
    String lessonStatus,
    @Schema(description = "变更类型(STOP/MAKEUP/ADJUST/RESCHEDULE)", allowableValues = {"STOP", "MAKEUP", "ADJUST", "RESCHEDULE"})
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
