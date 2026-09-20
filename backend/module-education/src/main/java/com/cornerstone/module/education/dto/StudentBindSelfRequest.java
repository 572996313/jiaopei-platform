package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "学生绑定自己档案请求(学号+姓名双因子验证;绑定后不可换绑)")
public record StudentBindSelfRequest(
    @Schema(description = "学员编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "学员编号不能为空")
    @Size(max = 64, message = "学员编号不能超过64个字符")
    String studentNo,

    @Schema(description = "学员姓名(必须与档案一致)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "学员姓名不能为空")
    @Size(max = 32, message = "学员姓名不能超过32个字符")
    String studentName
) {
}