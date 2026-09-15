package com.jiaopei.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "学生档案新建/编辑请求(仅开放姓名/性别/生日)")
public record StudentProfileSaveRequest(
    @Schema(description = "学员姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "学员姓名不能为空")
    @Size(max = 32, message = "学员姓名不能超过32个字符")
    String studentName,

    @Schema(description = "性别(MALE=男,FEMALE=女,UNKNOWN=未知)", allowableValues = {"MALE", "FEMALE", "UNKNOWN"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "性别不能为空")
    @Size(max = 16, message = "性别取值不正确")
    String gender,

    @Schema(description = "出生日期(选填)")
    LocalDate birthday
) {
}