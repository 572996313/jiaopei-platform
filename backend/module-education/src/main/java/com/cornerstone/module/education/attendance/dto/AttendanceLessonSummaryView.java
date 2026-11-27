package com.cornerstone.module.education.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "课次出勤汇总视图")
public record AttendanceLessonSummaryView(
    @Schema(description = "应到人数")
    int expectedCount,
    @Schema(description = "已打卡人数")
    int markedCount,
    @Schema(description = "实到人数")
    int presentCount,
    @Schema(description = "缺勤人数")
    int absentCount,
    @Schema(description = "请假人数")
    int leaveCount,
    @Schema(description = "迟到人数")
    int lateCount
) {
}
