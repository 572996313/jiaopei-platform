package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "学员更新请求")
public record StudentUpdateRequest(
    @Schema(description = "校区ID")
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @Schema(description = "学员编号")
    @JsonAlias({"code", "student_code"})
    String studentNo,
    @Schema(description = "学员姓名")
    @JsonAlias({"name", "student_name"})
    String studentName,
    @Schema(description = "性别(MALE=男,FEMALE=女,UNKNOWN=未知)", allowableValues = {"MALE", "FEMALE", "UNKNOWN"})
    String gender,
    @Schema(description = "出生日期")
    LocalDate birthday,
    @Schema(description = "监护人姓名")
    @JsonAlias({"parentName", "guardian_name"})
    String guardianName,
    @Schema(description = "监护人联系电话")
    @JsonAlias({"parentPhone", "guardian_phone"})
    String guardianPhone,
    @Schema(description = "状态(ENABLED=在读,GRADUATED=已结业,QUIT=已退学,DISABLED=停用)", allowableValues = {"ENABLED", "GRADUATED", "QUIT", "DISABLED"})
    String status,
    @Schema(description = "入学日期")
    @JsonAlias("enrolled_at")
    LocalDate enrolledAt,
    @Schema(description = "备注")
    String remark
) {
}
