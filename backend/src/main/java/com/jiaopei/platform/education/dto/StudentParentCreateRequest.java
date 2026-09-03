package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "学员家长创建请求")
public record StudentParentCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @Schema(description = "学员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "studentId is required")
    Long studentId,
    @Schema(description = "家长姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"parentName", "parent_name", "guardian_name"})
    @NotBlank(message = "parentName is required")
    String parentName,
    @Schema(description = "关系(FATHER/MOTHER/GRANDPA/GRANDMA/OTHER)", allowableValues = {"FATHER", "MOTHER", "GRANDPA", "GRANDMA", "OTHER"})
    @JsonAlias("relation_type")
    String relationType,
    @Schema(description = "家长联系电话")
    @JsonAlias({"parentPhone", "parent_phone", "guardian_phone"})
    String parentPhone,
    @Schema(description = "微信号")
    @JsonAlias("wechat_no")
    String wechatNo,
    @Schema(description = "是否主联系人")
    @JsonAlias("is_primary")
    Boolean isPrimary,
    @Schema(description = "是否紧急联系人")
    @JsonAlias("is_emergency_contact")
    Boolean isEmergencyContact,
    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)", allowableValues = {"ENABLED", "DISABLED"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
