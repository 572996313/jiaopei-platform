package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 班级行内的按月小计,季档展开行用它展示各月点名与出勤情况,不再额外拉课次明细。
 */
@Schema(description = "考勤按月小计视图")
public record AttendanceMonthSubtotalView(
    @Schema(description = "月份(YYYY-MM)") String month,
    @Schema(description = "该月课次数") int lessonCount,
    @Schema(description = "该月已点名课次数") int fullyMarkedLessons,
    @Schema(description = "该月未点名课次数") int pendingLessons,
    @Schema(description = "该月应到人次") int expectedCount,
    @Schema(description = "该月出勤人次") int presentCount,
    @Schema(description = "该月迟到人次") int lateCount,
    @Schema(description = "该月请假人次") int leaveCount,
    @Schema(description = "该月缺勤人次") int absentCount,
    @Schema(description = "该月出勤率(百分比整数,无应到学员时为 null)") Integer attendanceRate
) {
}
