package com.jiaopei.platform.education.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceLessonInfoView(
    Long lessonId,
    Long institutionId,
    Long campusId,
    Long classId,
    String className,
    LocalDate lessonDate,
    LocalTime startTime,
    LocalTime endTime,
    String lessonTitle,
    String lessonStatus,
    String remark
) {
}
