package com.jiaopei.platform.education.dto;

import java.util.List;

public record AttendanceLessonPageView(
    AttendanceLessonInfoView lesson,
    List<AttendanceLessonStudentView> students,
    AttendanceLessonSummaryView summary,
    String message
) {
}
