package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "注册凭证视图")
public record InvitationView(
    @Schema(description = "凭证ID") Long id,
    @Schema(description = "凭证码") String code,
    @Schema(description = "凭证类型(INVITE=邀请注册,ACTIVATE=激活预建账号)") String kind,
    @Schema(description = "归属机构ID") Long institutionId,
    @Schema(description = "归属机构名称") String institutionName,
    @Schema(description = "待激活账号ID(仅ACTIVATE)") Long userId,
    @Schema(description = "待激活账号用户名(仅ACTIVATE)") String username,
    @Schema(description = "注册后分配的角色编码(仅INVITE)") String roleCode,
    @Schema(description = "角色名称(仅INVITE)") String roleName,
    @Schema(description = "最大可用次数(空为不限)") Integer maxUses,
    @Schema(description = "已使用次数") Integer usedCount,
    @Schema(description = "过期时间(空为不过期)") LocalDateTime expiresAt,
    @Schema(description = "状态(active=启用,disabled=停用)") String status,
    @Schema(description = "备注") String remark,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {
}
