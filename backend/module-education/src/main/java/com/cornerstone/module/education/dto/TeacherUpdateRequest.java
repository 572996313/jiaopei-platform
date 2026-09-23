package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "教师更新请求")
public record TeacherUpdateRequest(
    @Schema(description = "校区ID")
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @Schema(description = "教师编号")
    @JsonAlias({"code", "teacher_code"})
    String teacherCode,
    @Schema(description = "教师姓名")
    @JsonAlias({"name", "teacher_name"})
    String teacherName,
    @Schema(description = "联系电话")
    @JsonAlias({"mobile", "teacher_phone"})
    String phone,
    @Schema(description = "性别字典(gender:male=男,female=女,unknown=未知)", allowableValues = {"male", "female", "unknown"})
    String gender,
    @Schema(description = "职称(如 初级/中级/高级)")
    String title,
    @Schema(description = "雇佣类型(FULL_TIME=全职,PART_TIME=兼职,OUTSOURCE=外聘)", allowableValues = {"FULL_TIME", "PART_TIME", "OUTSOURCE"})
    @JsonAlias("employment_type")
    String employmentType,
    @Schema(description = "状态(ENABLED=在职,DISABLED=离职)", allowableValues = {"ENABLED", "DISABLED"})
    String status,
    @Schema(description = "入职日期")
    @JsonAlias("hired_at")
    LocalDate hiredAt,
    @Schema(description = "备注")
    String remark
) {
}