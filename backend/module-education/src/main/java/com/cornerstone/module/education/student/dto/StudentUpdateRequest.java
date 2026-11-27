package com.cornerstone.module.education.student.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "学员更新请求")
public record StudentUpdateRequest(
    @Schema(description = "学员编号")
    @JsonAlias({"code", "student_code"})
    String studentNo,
    @Schema(description = "学员姓名")
    @JsonAlias({"name", "student_name"})
    String studentName,
    @Schema(description = "性别字典(gender:male=男,female=女,unknown=未知)", allowableValues = {"male", "female", "unknown"})
    String gender,
    @Schema(description = "出生日期")
    LocalDate birthday,
    @Schema(description = "监护人姓名")
    @JsonAlias({"parentName", "guardian_name"})
    String guardianName,
    @Schema(description = "监护人联系电话")
    @JsonAlias({"parentPhone", "guardian_phone"})
    String guardianPhone,
    @Schema(description = "学员状态字典(student_status:studying=在读,suspended=停课,graduated=已结业,withdrawn=已退学)", allowableValues = {"studying", "suspended", "graduated", "withdrawn"})
    String status,
    @Schema(description = "入学日期")
    @JsonAlias("enrolled_at")
    LocalDate enrolledAt,
    @Schema(description = "备注")
    String remark
) {
}
