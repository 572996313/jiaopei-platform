package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AttendanceCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,
    @JsonAlias("lesson_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long lessonId,
    @JsonAlias({"class_id", "teachingClassId"})
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "classId is required")
    Long classId,
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "studentId is required")
    Long studentId,
    @JsonAlias({"date", "attendance_date"})
    @NotNull(message = "attendanceDate is required")
    LocalDate attendanceDate,
    @JsonAlias({"status", "attendance_status"})
    String attendanceStatus,
    String source,
    String remark
) {
}
