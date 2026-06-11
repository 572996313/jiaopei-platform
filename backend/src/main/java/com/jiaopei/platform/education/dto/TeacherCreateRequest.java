package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TeacherCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @JsonAlias({"code", "teacher_code"})
    @NotBlank(message = "teacherCode is required")
    String teacherCode,
    @JsonAlias({"name", "teacher_name"})
    @NotBlank(message = "teacherName is required")
    String teacherName,
    @JsonAlias({"mobile", "teacher_phone"})
    String phone,
    String gender,
    String title,
    @JsonAlias("employment_type")
    String employmentType,
    String status,
    @JsonAlias("hired_at")
    LocalDate hiredAt,
    String remark
) {
}
