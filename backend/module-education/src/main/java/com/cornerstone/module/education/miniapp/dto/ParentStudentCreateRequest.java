package com.cornerstone.module.education.miniapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "家长代建学员档案请求(建档后自动绑定到当前家长账号)")
public record ParentStudentCreateRequest(
    @Schema(description = "学员姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "学员姓名不能为空")
    @Size(max = 32, message = "学员姓名不能超过32个字符")
    String studentName,

    @Schema(description = "性别字典(gender:male=男,female=女,unknown=未知)", allowableValues = {"male", "female", "unknown"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "性别不能为空")
    @Size(max = 16, message = "性别取值不正确")
    String gender,

    @Schema(description = "出生日期(选填,不能晚于今天)")
    LocalDate birthday,

    @Schema(description = "监护人姓名(选填;与电话一起填齐时会同步登记为家长联系人)")
    @Size(max = 32, message = "监护人姓名不能超过32个字符")
    String guardianName,

    @Schema(description = "监护人联系电话(选填;填了才能按手机号再次绑定该学员)")
    @Size(max = 20, message = "监护人联系电话不能超过20个字符")
    String guardianPhone
) {
}
