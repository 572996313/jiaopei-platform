package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
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
    @Schema(description = "课程编码(可留空,留空时按编号规则自动生成)")
    @JsonAlias({"code", "course_code"})
    // 可留空：留空时由编号规则补号，判空在 Service 里做
    String courseCode,
    @Schema(description = "课程名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "course_name"})
    @NotBlank(message = "课程名称不能为空")
    String courseName,
    @Schema(description = "学科名称(如 数学/英语)")
    @JsonAlias({"subject", "subject_name"})
    String subjectName,
    @Schema(description = "授课方式字典(teaching_method:offline_small_class=线下小班,offline_large_class=线下大班,offline_one_to_one=线下1对1,online_small_class=线上小班,online_large_class=线上大班,online_one_to_one=线上1对1,offline=线下,online=线上,hybrid=混合)", allowableValues = {"offline_small_class", "offline_large_class", "offline_one_to_one", "online_small_class", "online_large_class", "online_one_to_one", "offline", "online", "hybrid"})
    @JsonAlias("teaching_method")
    String teachingMethod,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
