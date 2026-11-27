package com.cornerstone.module.education.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 课次点名进度视图,GET /api/education/attendance/lesson-progress 批量返回,
 * 供日课表一次性展示各课次点名进度,避免逐课次拉取全量点名页。
 */
@Schema(description = "课次点名进度视图")
public record AttendanceLessonProgressView(
    @Schema(description = "课次 ID") Long lessonId,
    @Schema(description = "应到人数(课次日期在班学员数)") int expectedCount,
    @Schema(description = "已点名人数") int markedCount,
    @Schema(description = "出勤人数") int presentCount,
    @Schema(description = "缺勤人数") int absentCount,
    @Schema(description = "请假人数") int leaveCount,
    @Schema(description = "迟到人数") int lateCount
) {
}
