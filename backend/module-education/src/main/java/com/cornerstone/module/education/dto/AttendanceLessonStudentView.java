package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "课次出勤学员视图")
public record AttendanceLessonStudentView(
    @Schema(description = "学员ID")
    Long studentId,
    @Schema(description = "学员编号")
    String studentNo,
    @Schema(description = "学员姓名")
    String studentName,
    @Schema(description = "出勤记录ID(未打卡时为null)")
    Long attendanceId,
    @Schema(description = "出勤状态(present=出勤,absent=缺勤,late=迟到,leave=请假)", allowableValues = {"present", "absent", "late", "leave"})
    String attendanceStatus,
    @Schema(description = "数据来源(MANUAL/AUTO/IMPORT)")
    String source,
    @Schema(description = "备注")
    String remark
) {
}
