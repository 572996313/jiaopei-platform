package com.jiaopei.platform.education.dto;

public record AttendanceLessonSummaryView(
    int expectedCount,
    int markedCount,
    int presentCount,
    int absentCount,
    int leaveCount,
    int lateCount
) {
}
