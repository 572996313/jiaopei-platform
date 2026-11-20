package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 家长联系人视图,分页列表用,已回带学员名称,前端无需全量加载学员做名称回显。
 */
@Schema(description = "家长联系人视图(含学员名称)")
public record StudentParentView(
    @Schema(description = "联系人记录 ID") Long id,
    @Schema(description = "机构 ID(多租户隔离)") Long institutionId,
    @Schema(description = "学员 ID") Long studentId,
    @Schema(description = "学号") String studentNo,
    @Schema(description = "学员姓名") String studentName,
    @Schema(description = "家长姓名") String parentName,
    @Schema(description = "家长关系字典(parent_relation:mother=母亲,father=父亲,guardian=监护人,relative=其他亲属)") String relationType,
    @Schema(description = "家长联系电话") String parentPhone,
    @Schema(description = "微信号") String wechatNo,
    @Schema(description = "支付宝绑定电话") String alipayPhone,
    @Schema(description = "是否主联系人") Boolean isPrimary,
    @Schema(description = "是否紧急联系人") Boolean isEmergencyContact,
    @Schema(description = "状态(active=启用,inactive=停用)") String status,
    @Schema(description = "备注") String remark
) {
}
