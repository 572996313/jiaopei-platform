package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_student_parent")
@Schema(description = "学员家长关系")
public class StudentParentEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "学员ID")
    private Long studentId;

    @Schema(description = "家长姓名")
    private String parentName;

    @Schema(description = "关系(FATHER/MOTHER/GRANDPA/GRANDMA/OTHER)")
    private String relationType;

    @Schema(description = "家长联系电话")
    private String parentPhone;

    @Schema(description = "微信号")
    private String wechatNo;

    @Schema(description = "是否主联系人")
    private Boolean isPrimary;

    @Schema(description = "是否紧急联系人")
    private Boolean isEmergencyContact;

    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
