package com.cornerstone.core.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 第三方登录渠道配置。一个渠道编码对应一个登录入口，端点与密钥均可运行期维护，
 * 新增标准 OAuth2 渠道不需要改代码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_provider")
@Schema(description = "第三方登录渠道配置")
public class ThirdPartyAuthProviderEntity extends BaseEntity {

    @Schema(description = "渠道编码(唯一，如 linuxdo)")
    private String providerCode;

    @Schema(description = "渠道名称(登录页展示)")
    private String providerName;

    @Schema(description = "渠道类型(OAUTH2=标准OAuth2,WECHAT_MINIAPP=微信小程序)")
    private String providerType;

    @Schema(description = "图标标识(前端按标识选内置图标)")
    private String icon;

    @Schema(description = "排序号(登录页展示顺序)")
    private Integer sortNo;

    @Schema(description = "状态(active=启用,disabled=停用)")
    private String status;

    @Schema(description = "客户端ID")
    private String clientId;

    @Schema(description = "客户端密钥(AES-GCM 密文，enc:v1: 前缀)")
    private String clientSecret;

    @Schema(description = "授权地址")
    private String authorizeUrl;

    @Schema(description = "令牌地址")
    private String tokenUrl;

    @Schema(description = "用户信息地址")
    private String userinfoUrl;

    @Schema(description = "授权范围(scope，多个用空格分隔)")
    private String scope;

    @Schema(description = "令牌请求客户端认证方式(post=表单提交,basic=HTTP Basic)")
    private String tokenAuthMethod;

    @Schema(description = "回调地址(需先在渠道后台登记)")
    private String redirectUri;

    @Schema(description = "渠道扩展配置 JSON(如用户字段映射)")
    private String extraConfig;

    @Schema(description = "备注")
    private String remark;
}
