package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "报名创建请求")
public record EnrollmentCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "学员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "学员ID不能为空")
    Long studentId,
    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"class_id", "teachingClassId"})
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "班级ID不能为空")
    Long classId,
    @Schema(description = "报名编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"code", "enrollment_no"})
    @NotBlank(message = "报名编号不能为空")
    String enrollmentNo,
    @Schema(description = "报名渠道字典(enrollment_channel:front_desk=前台报名,consultant=顾问录入,online=线上线索,referral=转介绍)", allowableValues = {"front_desk", "consultant", "online", "referral"})
    String channel,
    @Schema(description = "状态(PENDING=待审核,APPROVED=已通过,REJECTED=已驳回,CANCELLED=已取消)", allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"})
    String status,
    @Schema(description = "报名日期")
    @JsonAlias("enrolled_at")
    LocalDate enrolledAt,
    @Schema(description = "备注")
    String remark
) {
}
