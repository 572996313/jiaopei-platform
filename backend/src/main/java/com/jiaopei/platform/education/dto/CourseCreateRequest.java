package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CourseCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @JsonAlias("category_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long categoryId,
    @JsonAlias({"code", "course_code"})
    @NotBlank(message = "courseCode is required")
    String courseCode,
    @JsonAlias({"name", "course_name"})
    @NotBlank(message = "courseName is required")
    String courseName,
    @JsonAlias({"subject", "subject_name"})
    String subjectName,
    @JsonAlias("teaching_method")
    String teachingMethod,
    String status,
    String remark
) {
}
