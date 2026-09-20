package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "课程更新请求")
public record CourseUpdateRequest(
    @Schema(description = "课程分类ID")
    @JsonAlias("category_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long categoryId,
    @Schema(description = "课程编码")
    @JsonAlias({"code", "course_code"})
    String courseCode,
    @Schema(description = "课程名称")
    @JsonAlias({"name", "course_name"})
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