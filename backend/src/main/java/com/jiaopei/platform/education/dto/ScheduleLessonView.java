package com.jiaopei.platform.education.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ScheduleLessonView(
    Long id,
    Long institutionId,
    Long campusId,
    Long termId,
    Long classId,
    Long classroomId,
    Long timeSlotId,
    LocalDate lessonDate,
    Integer lessonWeekday,
    LocalTime startTime,
    LocalTime endTime,
    String lessonTitle,
    String lessonStatus,
    List<Long> teacherIds,
    String remark
) {
}
