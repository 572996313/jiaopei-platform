package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "教师创建请求")
public record TeacherCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "校区ID")
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @Schema(description = "教师编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"code", "teacher_code"})
    @NotBlank(message = "教师编码不能为空")
    String teacherCode,
    @Schema(description = "教师姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "teacher_name"})
    @NotBlank(message = "教师姓名不能为空")
    String teacherName,
    @Schema(description = "联系电话")
    @JsonAlias({"mobile", "teacher_phone"})
    String phone,
    @Schema(description = "性别字典(gender:male=男,female=女,unknown=未知)", allowableValues = {"male", "female", "unknown"})
    String gender,
    @Schema(description = "职称(如 初级/中级/高级)")
    String title,
    @Schema(description = "用工类型字典(employment_type:full_time=全职,part_time=兼职,contract=外聘)", allowableValues = {"full_time", "part_time", "contract"})
    @JsonAlias("employment_type")
    String employmentType,
    @Schema(description = "状态(active=在职,suspended=暂停,left=离职)", allowableValues = {"active", "suspended", "left"})
    String status,
    @Schema(description = "入职日期")
    @JsonAlias("hired_at")
    LocalDate hiredAt,
    @Schema(description = "备注")
    String remark
) {
}
