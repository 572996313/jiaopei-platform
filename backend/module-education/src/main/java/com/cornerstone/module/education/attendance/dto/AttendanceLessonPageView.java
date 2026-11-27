package com.cornerstone.module.education.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "课次出勤页面视图")
public record AttendanceLessonPageView(
    @Schema(description = "课次信息")
    AttendanceLessonInfoView lesson,
    @Schema(description = "学员出勤明细列表")
    List<AttendanceLessonStudentView> students,
    @Schema(description = "出勤汇总统计")
    AttendanceLessonSummaryView summary,
    @Schema(description = "附加提示信息")
    String message
) {
}
