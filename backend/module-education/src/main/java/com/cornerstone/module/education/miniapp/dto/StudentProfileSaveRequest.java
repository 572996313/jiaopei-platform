package com.cornerstone.module.education.miniapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "学生档案新建/编辑请求(仅开放姓名/性别/生日/监护人)")
public record StudentProfileSaveRequest(
    @Schema(description = "学员姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "学员姓名不能为空")
    @Size(max = 32, message = "学员姓名不能超过32个字符")
    String studentName,

    @Schema(description = "性别字典(gender:male=男,female=女,unknown=未知)", allowableValues = {"male", "female", "unknown"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "性别不能为空")
    @Size(max = 16, message = "性别取值不正确")
    String gender,

    @Schema(description = "出生日期(选填)")
    LocalDate birthday,

    @Schema(description = "监护人姓名(选填,留空即清空)")
    @Size(max = 32, message = "监护人姓名不能超过32个字符")
    String guardianName,

    @Schema(description = "监护人联系电话(选填,留空即清空)")
    @Size(max = 20, message = "监护人联系电话不能超过20个字符")
    String guardianPhone
) {
}