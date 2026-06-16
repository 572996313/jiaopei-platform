package com.jiaopei.platform.education.dto;

public record AttendanceLessonStudentView(
    Long studentId,
    String studentNo,
    String studentName,
    Long attendanceId,
    String attendanceStatus,
    String source,
    String remark
) {
}
