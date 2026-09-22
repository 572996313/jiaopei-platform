package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 无密码建号入参：业务身份（如微信 openid）兑换邀请码开新账号时由业务侧传入。
 *
 * <p>与 {@link InvitationRegisterRequest} 的区别：这里不带密码，账号建为无密码态
 * （passwordHash 为空，账号密码登录在入口直接拒绝）；账号名与来源标记由业务决定，
 * 机构与角色仍一律由邀请码固定。登录令牌由调用方自行签发，底座不签。</p>
 */
@Schema(description = "邀请码无密码建号入参(业务身份兑换用)")
public record InvitationAccountSpec(

    @Schema(description = "邀请码", requiredMode = Schema.RequiredMode.REQUIRED)
    String code,

    @Schema(description = "账号名(由业务身份决定,如微信 openid 前缀),全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    @Schema(description = "显示名称")
    String displayName,

    @Schema(description = "手机号")
    String phone,

    @Schema(description = "账号来源标记(sys_user_account.register_source),留空默认 INVITATION_CODE")
    String registerSource
) {
}
