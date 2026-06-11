package com.jiaopei.platform.education.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ScheduleLessonRequest(
    @NotNull Long institutionId,
    @NotNull Long campusId,
    @NotNull Long termId,
    @NotNull Long classId,
    @NotNull Long classroomId,
    @NotNull Long timeSlotId,
    @NotNull LocalDate lessonDate,
    @Size(max = 128) String lessonTitle,
    @Size(max = 32) String lessonStatus,
    @NotNull List<Long> teacherIds,
    @Size(max = 500) String remark
) {
}
