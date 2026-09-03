package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "课程分类创建请求")
public record CourseCategoryCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @Schema(description = "分类编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"code", "category_code"})
    @NotBlank(message = "categoryCode is required")
    String categoryCode,
    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "category_name"})
    @NotBlank(message = "categoryName is required")
    String categoryName,
    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)", allowableValues = {"ENABLED", "DISABLED"})
    String status,
    @Schema(description = "排序号(越小越靠前)")
    @JsonAlias("sort_order")
    @Min(value = 0, message = "sortOrder must be greater than or equal to 0")
    Integer sortOrder,
    @Schema(description = "备注")
    String remark
) {
}
