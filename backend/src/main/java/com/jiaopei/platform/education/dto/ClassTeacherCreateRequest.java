package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ClassTeacherCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @JsonAlias("class_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "classId is required")
    Long classId,
    @JsonAlias("teacher_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "teacherId is required")
    Long teacherId,
    @JsonAlias("teacher_role")
    String teacherRole,
    @JsonAlias("is_primary")
    Boolean isPrimary,
    @JsonAlias("joined_at")
    LocalDate joinedAt,
    @JsonAlias("left_at")
    LocalDate leftAt,
    String status,
    String remark
) {
}
