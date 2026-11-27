package com.cornerstone.module.education.miniapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "绑定学员档案请求(通过学员编号或监护人手机号+学员姓名双重验证绑定)")
public record BindStudentRequest(
    @Schema(description = "绑定方式(STUDENT_NO=按学员编号,PARENT_PHONE=按监护人手机号)",
            allowableValues = {"STUDENT_NO", "PARENT_PHONE"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "绑定方式不能为空")
    String method,

    @Schema(description = "学员编号或监护人手机号(与 method 对应)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "学员编号或手机号不能为空")
    @Size(max = 64, message = "学员编号或手机号不能超过64个字符")
    String value,

    @Schema(description = "学员姓名(双因子验证,必须与档案一致,防止陌生人乱绑)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "学员姓名不能为空")
    @Size(max = 32, message = "学员姓名不能超过32个字符")
    String studentName,

    @Schema(description = "备注(如\"大儿子\"\"侄子\"等)")
    @Size(max = 100, message = "备注不能超过100个字符")
    String remark
) {
}
