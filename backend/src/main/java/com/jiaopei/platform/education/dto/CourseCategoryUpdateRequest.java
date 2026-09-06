package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "课程分类更新请求")
public record CourseCategoryUpdateRequest(
    @Schema(description = "分类编码")
    @JsonAlias({"code", "category_code"})
    String categoryCode,
    @Schema(description = "分类名称")
    @JsonAlias({"name", "category_name"})
    String categoryName,
    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)", allowableValues = {"ENABLED", "DISABLED"})
    String status,
    @Schema(description = "排序号(越小越靠前)")
    @JsonAlias("sort_order")
    @Min(value = 0, message = "sortOrder 必须大于或等于 0")
    Integer sortOrder,
    @Schema(description = "备注")
    String remark
) {
}