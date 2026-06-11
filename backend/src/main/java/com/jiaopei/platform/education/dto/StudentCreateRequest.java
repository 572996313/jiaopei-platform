package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record StudentCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @JsonAlias({"code", "student_code"})
    @NotBlank(message = "studentNo is required")
    String studentNo,
    @JsonAlias({"name", "student_name"})
    @NotBlank(message = "studentName is required")
    String studentName,
    String gender,
    LocalDate birthday,
    @JsonAlias({"parentName", "guardian_name"})
    String guardianName,
    @JsonAlias({"parentPhone", "guardian_phone"})
    String guardianPhone,
    String status,
    @JsonAlias("enrolled_at")
    LocalDate enrolledAt,
    String remark
) {
}
