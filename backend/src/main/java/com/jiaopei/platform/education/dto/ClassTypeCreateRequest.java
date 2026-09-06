package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "班级类型创建请求")
public record ClassTypeCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "类型编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"code", "type_code"})
    @NotBlank(message = "班级类型编码不能为空")
    String typeCode,
    @Schema(description = "类型名称(如 一对一/小班/大班)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "type_name"})
    @NotBlank(message = "班级类型名称不能为空")
    String typeName,
    @Schema(description = "授课方式(取值来自字典 teaching_method,如 offline_small_class=线下小班、online_one_to_one=线上1对1)")
    @JsonAlias("teaching_method")
    String teachingMethod,
    @Schema(description = "最小开班人数")
    @JsonAlias("min_students")
    @Min(value = 0, message = "最小学员数必须大于或等于0")
    Integer minStudents,
    @Schema(description = "最大开班人数")
    @JsonAlias("max_students")
    @Min(value = 0, message = "最大学员数必须大于或等于0")
    Integer maxStudents,
    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)", allowableValues = {"ENABLED", "DISABLED"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
