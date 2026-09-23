package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "学员创建请求")
public record StudentCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "校区ID")
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @Schema(description = "学员编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"code", "student_code"})
    @NotBlank(message = "学号不能为空")
    String studentNo,
    @Schema(description = "学员姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "student_name"})
    @NotBlank(message = "学员姓名不能为空")
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
    @Schema(description = "状态(ENABLED=在读,GRADUATED=已结业,QUIT=已退学,DISABLED=停用)", allowableValues = {"ENABLED", "GRADUATED", "QUIT", "DISABLED"})
    String status,
    @Schema(description = "入学日期")
    @JsonAlias("enrolled_at")
    LocalDate enrolledAt,
    @Schema(description = "备注")
    String remark
) {
}
