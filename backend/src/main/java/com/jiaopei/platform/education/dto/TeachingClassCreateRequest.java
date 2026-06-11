package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TeachingClassCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "campusId is required")
    Long campusId,
    @JsonAlias("term_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long termId,
    @JsonAlias({"code", "class_code"})
    @NotBlank(message = "classCode is required")
    String classCode,
    @JsonAlias({"name", "class_name"})
    @NotBlank(message = "className is required")
    String className,
    @JsonAlias({"primary_teacher_id", "teacher_id"})
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long teacherId,
    @JsonAlias({"capacity", "capacity_limit"})
    @Min(value = 0, message = "capacityLimit must be greater than or equal to 0")
    Integer capacityLimit,
    @JsonAlias("current_students")
    @Min(value = 0, message = "currentStudents must be greater than or equal to 0")
    Integer currentStudents,
    String status,
    @JsonAlias("opened_at")
    LocalDate openedAt,
    String remark
) {
}
