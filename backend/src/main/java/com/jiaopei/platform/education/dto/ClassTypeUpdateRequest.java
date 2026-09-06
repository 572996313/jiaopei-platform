package com.jiaopei.platform.education.dto;

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
    @Schema(description = "授课方式(ONLINE=线上,OFFLINE=线下,HYBRID=混合)", allowableValues = {"ONLINE", "OFFLINE", "HYBRID"})
    @JsonAlias("teaching_method")
    String teachingMethod,
    @Schema(description = "最小开班人数")
    @JsonAlias("min_students")
    @Min(value = 0, message = "minStudents 必须大于或等于 0")
    Integer minStudents,
    @Schema(description = "最大开班人数")
    @JsonAlias("max_students")
    @Min(value = 0, message = "maxStudents 必须大于或等于 0")
    Integer maxStudents,
    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)", allowableValues = {"ENABLED", "DISABLED"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}