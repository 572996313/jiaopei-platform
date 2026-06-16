package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClassTypeCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @JsonAlias({"code", "type_code"})
    @NotBlank(message = "typeCode is required")
    String typeCode,
    @JsonAlias({"name", "type_name"})
    @NotBlank(message = "typeName is required")
    String typeName,
    @JsonAlias("teaching_method")
    String teachingMethod,
    @JsonAlias("min_students")
    @Min(value = 0, message = "minStudents must be greater than or equal to 0")
    Integer minStudents,
    @JsonAlias("max_students")
    @Min(value = 0, message = "maxStudents must be greater than or equal to 0")
    Integer maxStudents,
    String status,
    String remark
) {
}
