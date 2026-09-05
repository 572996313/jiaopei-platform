package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
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
    @Schema(description = "授课方式(ONLINE=线上,OFFLINE=线下,HYBRID=混合)", allowableValues = {"ONLINE", "OFFLINE", "HYBRID"})
    @JsonAlias("teaching_method")
    String teachingMethod,
    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)", allowableValues = {"ENABLED", "DISABLED"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}