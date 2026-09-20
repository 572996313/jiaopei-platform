package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "班级类型更新请求")
public record ClassTypeUpdateRequest(
    @Schema(description = "类型编码")
    @JsonAlias({"code", "type_code"})
    String typeCode,
    @Schema(description = "类型名称(如 一对一/小班/大班)")
    @JsonAlias({"name", "type_name"})
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