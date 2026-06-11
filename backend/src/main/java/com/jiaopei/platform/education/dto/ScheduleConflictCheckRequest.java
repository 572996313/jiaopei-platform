package com.jiaopei.platform.education.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record ScheduleConflictCheckRequest(
    Long excludeLessonId,
    @NotNull Long institutionId,
    @NotNull Long campusId,
    @NotNull Long termId,
    @NotNull Long classId,
    @NotNull Long classroomId,
    @NotNull Long timeSlotId,
    @NotNull LocalDate lessonDate,
    @NotNull List<Long> teacherIds
) {
}
