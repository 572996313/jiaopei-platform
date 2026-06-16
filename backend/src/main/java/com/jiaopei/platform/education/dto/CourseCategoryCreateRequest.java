package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CourseCategoryCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @JsonAlias({"code", "category_code"})
    @NotBlank(message = "categoryCode is required")
    String categoryCode,
    @JsonAlias({"name", "category_name"})
    @NotBlank(message = "categoryName is required")
    String categoryName,
    String status,
    @JsonAlias("sort_order")
    @Min(value = 0, message = "sortOrder must be greater than or equal to 0")
    Integer sortOrder,
    String remark
) {
}
