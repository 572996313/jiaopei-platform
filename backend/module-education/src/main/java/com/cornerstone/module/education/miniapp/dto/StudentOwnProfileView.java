package com.cornerstone.module.education.miniapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "学生端自己的档案视图(未绑定档案时 bound=false)")
public record StudentOwnProfileView(
    @Schema(description = "是否已绑定档案(false=未绑定,前端引导绑定或新建)")
    Boolean bound,
    @Schema(description = "学员ID(bound 时非空)")
    Long studentId,
    @Schema(description = "学员编号")
    String studentNo,
    @Schema(description = "学员姓名")
    String studentName,
    @Schema(description = "性别字典(gender:male=男,female=女,unknown=未知)", allowableValues = {"male", "female", "unknown"})
    String gender,
    @Schema(description = "出生日期")
    LocalDate birthday,
    @Schema(description = "监护人姓名")
    String guardianName,
    @Schema(description = "监护人联系电话")
    String guardianPhone,
    @Schema(description = "班级名称(当前所在班,无则为空)")
    String className
) {
}