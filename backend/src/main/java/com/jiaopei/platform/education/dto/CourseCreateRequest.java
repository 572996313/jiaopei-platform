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
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "课程分类ID")
    @JsonAlias("category_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long categoryId,
    @Schema(description = "课程编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"code", "course_code"})
    @NotBlank(message = "课程编号不能为空")
    String courseCode,
    @Schema(description = "课程名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "course_name"})
    @NotBlank(message = "课程名称不能为空")
    String courseName,
    @Schema(description = "学科名称(如 数学/英语)")
    @JsonAlias({"subject", "subject_name"})
    String subjectName,
    @Schema(description = "授课方式(取值来自字典 teaching_method,如 offline_small_class=线下小班、online_one_to_one=线上1对1)")
    @JsonAlias("teaching_method")
    String teachingMethod,
    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)", allowableValues = {"ENABLED", "DISABLED"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
