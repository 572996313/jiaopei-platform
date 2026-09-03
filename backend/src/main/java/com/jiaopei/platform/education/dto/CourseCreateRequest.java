package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "课程创建请求")
public record CourseCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @Schema(description = "课程分类ID")
    @JsonAlias("category_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long categoryId,
    @Schema(description = "课程编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"code", "course_code"})
    @NotBlank(message = "courseCode is required")
    String courseCode,
    @Schema(description = "课程名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "course_name"})
    @NotBlank(message = "courseName is required")
    String courseName,
    @Schema(description = "学科名称(如 数学/英语)")
    @JsonAlias({"subject", "subject_name"})
    String subjectName,
    @Schema(description = "授课方式(ONLINE/OFFLINE/HYBRID)", allowableValues = {"ONLINE", "OFFLINE", "HYBRID"})
    @JsonAlias("teaching_method")
    String teachingMethod,
    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)", allowableValues = {"ENABLED", "DISABLED"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
