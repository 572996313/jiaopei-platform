package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "凭证预检结果(注册/激活页展示用,不泄露账号明细)")
public record InvitationPrecheckView(
    @Schema(description = "凭证是否可用") boolean valid,
    @Schema(description = "凭证类型(INVITE=邀请注册,ACTIVATE=激活预建账号)") String kind,
    @Schema(description = "归属机构名称") String institutionName,
    @Schema(description = "注册后分配的角色编码(仅INVITE)") String roleCode,
    @Schema(description = "角色名称(仅INVITE)") String roleName,
    @Schema(description = "过期时间(空为不过期)") LocalDateTime expiresAt,
    @Schema(description = "不可用原因,可用时为空") String message
) {
}
