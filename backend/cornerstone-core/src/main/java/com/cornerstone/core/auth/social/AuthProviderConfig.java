package com.cornerstone.core.auth.social;

/**
 * 渠道运行期配置（密钥已解密）。由 {@link AuthProviderRegistry} 从 sys_auth_provider 组装，
 * 渠道实现只读这份配置，不直接依赖实体。
 */
public record AuthProviderConfig(
    String providerCode,
    String providerType,
    String clientId,
    String clientSecret,
    String authorizeUrl,
    String tokenUrl,
    String userinfoUrl,
    String scope,
    String tokenAuthMethod,
    String redirectUri,
    String extraConfig
) {
}
