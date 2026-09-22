package com.cornerstone.core.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_invitation")
@Schema(description = "账号注册凭证(邀请码/激活码)")
public class AuthInvitationEntity extends BaseEntity {

    @Schema(description = "归属机构ID")
    private Long institutionId;

    @Schema(description = "凭证码(INV前缀=邀请码,ACT前缀=激活码)")
    private String code;

    @Schema(description = "凭证类型(INVITE=邀请注册,ACTIVATE=激活预建账号)",
            allowableValues = {"INVITE", "ACTIVATE"})
    private String kind;

    @Schema(description = "待激活账号ID(ACTIVATE必填,INVITE为空)")
    private Long userId;

    @Schema(description = "注册成功后自动分配的角色编码(INVITE必填,ACTIVATE忽略)")
    private String roleCode;

    @Schema(description = "最大可用次数(空为不限)")
    private Integer maxUses;

    @Schema(description = "已使用次数")
    private Integer usedCount;

    @Schema(description = "过期时间(空为不过期)")
    private LocalDateTime expiresAt;

    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    private String status;

    @Schema(description = "备注")
    private String remark;
}
