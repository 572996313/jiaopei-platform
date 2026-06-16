package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.NotNull;

public record AttendanceBatchItemRequest(
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "studentId is required")
    Long studentId,
    @JsonAlias({"status", "attendance_status"})
    String attendanceStatus,
    String source,
    String remark
) {
}
