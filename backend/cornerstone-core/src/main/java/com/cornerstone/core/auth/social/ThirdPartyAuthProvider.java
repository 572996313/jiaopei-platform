package com.cornerstone.core.auth.social;

/**
 * 第三方登录渠道实现的 SPI。
 *
 * <p>标准 OAuth2 渠道由底座自带的 {@code OAUTH2} 实现直接支持（只需在
 * sys_auth_provider 建一行配置）；微信小程序、公众号、网站扫码这类
 * 换票协议与 OAuth2 不同的渠道，由业务模块提供实现并注册为 Bean
 * （SDK 不进底座）。</p>
 */
public interface ThirdPartyAuthProvider {

    /** 渠道类型标识，与 sys_auth_provider.provider_type 对应 */
    String type();

    /** 拼装浏览器跳转的授权地址 */
    String buildAuthorizeUrl(AuthProviderConfig config, String state);

    /** 用授权码换取外部身份 */
    ExternalIdentity exchange(AuthProviderConfig config, String code);
}
