package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 第三方登录渠道的管理视图。密钥只回传"是否已配置"，不回传密文或明文，
 * 避免管理页与浏览器缓存成为第二份密钥存放处。
 */
@Schema(description = "第三方登录渠道配置(管理端)")
public record AuthProviderAdminView(
    @Schema(description = "配置ID") Long id,
    @Schema(description = "渠道编码(登录页与回调路径使用)") String providerCode,
    @Schema(description = "渠道名称") String providerName,
    @Schema(description = "渠道类型(OAUTH2/WECHAT_MINIAPP)") String providerType,
    @Schema(description = "图标标识") String icon,
    @Schema(description = "排序号") Integer sortNo,
    @Schema(description = "状态(active=启用,disabled=停用)") String status,
    @Schema(description = "客户端ID") String clientId,
    @Schema(description = "是否已配置客户端密钥") Boolean hasClientSecret,
    @Schema(description = "授权地址") String authorizeUrl,
    @Schema(description = "令牌地址") String tokenUrl,
    @Schema(description = "用户信息地址") String userinfoUrl,
    @Schema(description = "授权范围") String scope,
    @Schema(description = "令牌请求客户端认证方式(post/basic)") String tokenAuthMethod,
    @Schema(description = "回调地址") String redirectUri,
    @Schema(description = "渠道扩展配置JSON") String extraConfig,
    @Schema(description = "备注") String remark,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "更新时间") LocalDateTime updatedAt
) {
}
