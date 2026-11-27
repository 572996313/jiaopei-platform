package com.cornerstone.module.education.student.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "学员家长创建请求")
public record StudentParentCreateRequest(
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
    @Schema(description = "家长姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"parentName", "parent_name", "guardian_name"})
    @NotBlank(message = "家长姓名不能为空")
    String parentName,
    @Schema(description = "家长关系字典(parent_relation:mother=母亲,father=父亲,guardian=监护人,relative=其他亲属)", allowableValues = {"mother", "father", "guardian", "relative"})
    @JsonAlias("relation_type")
    String relationType,
    @Schema(description = "家长联系电话")
    @JsonAlias({"parentPhone", "parent_phone", "guardian_phone"})
    String parentPhone,
    @Schema(description = "微信号")
    @JsonAlias("wechat_no")
    String wechatNo,
    @Schema(description = "支付宝绑定电话")
    @JsonAlias("alipay_phone")
    String alipayPhone,
    @Schema(description = "是否主联系人")
    @JsonAlias("is_primary")
    Boolean isPrimary,
    @Schema(description = "是否紧急联系人")
    @JsonAlias("is_emergency_contact")
    Boolean isEmergencyContact,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
