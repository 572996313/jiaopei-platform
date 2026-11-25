package com.cornerstone.core.auth.social;

/**
 * 第三方渠道换取到的外部身份。openId 是渠道内不可变的唯一标识，
 * 昵称与头像只作展示快照，不参与身份判定。
 */
public record ExternalIdentity(
    String providerCode,
    String appType,
    String openId,
    String unionId,
    String nickname,
    String avatarUrl
) {
}
