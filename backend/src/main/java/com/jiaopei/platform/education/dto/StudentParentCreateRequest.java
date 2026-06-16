package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StudentParentCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "studentId is required")
    Long studentId,
    @JsonAlias({"parentName", "parent_name", "guardian_name"})
    @NotBlank(message = "parentName is required")
    String parentName,
    @JsonAlias("relation_type")
    String relationType,
    @JsonAlias({"parentPhone", "parent_phone", "guardian_phone"})
    String parentPhone,
    @JsonAlias("wechat_no")
    String wechatNo,
    @JsonAlias("is_primary")
    Boolean isPrimary,
    @JsonAlias("is_emergency_contact")
    Boolean isEmergencyContact,
    String status,
    String remark
) {
}
