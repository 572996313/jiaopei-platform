package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 第三方登录渠道的新增/编辑请求。
 *
 * <p>密钥留空表示"保持原值"：编辑时前端拿不到密文，也就无法回填，
 * 把空串当清空会让一次误保存把线上渠道弄坏。确实要换就重新填一个。</p>
 */
@Schema(description = "第三方登录渠道保存请求")
public record AuthProviderSaveRequest(
    @Schema(description = "渠道编码(仅新增时必填；小写字母开头，可含数字/下划线/短横线，建后不可改)")
    @Pattern(regexp = "^$|^[a-z][a-z0-9_-]{1,63}$",
        message = "渠道编码只能用小写字母、数字、下划线和短横线，且以字母开头")
    @Size(max = 64, message = "渠道编码不能超过 64 个字符")
    String providerCode,

    @Schema(description = "渠道名称(登录页展示)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 64, message = "渠道名称不能超过 64 个字符")
    String providerName,

    @Schema(description = "渠道类型(OAUTH2=标准OAuth2,WECHAT_MINIAPP=微信小程序)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "渠道类型不能为空")
    @Size(max = 32, message = "渠道类型不能超过 32 个字符")
    String providerType,

    @Schema(description = "图标标识(前端按标识选内置图标)")
    @Size(max = 64, message = "图标标识不能超过 64 个字符")
    String icon,

    @Schema(description = "排序号(越小越靠前)")
    Integer sortNo,

    @Schema(description = "状态(active=启用,disabled=停用)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "状态不能为空")
    @Size(max = 16, message = "状态不能超过 16 个字符")
    String status,

    @Schema(description = "客户端ID")
    @Size(max = 128, message = "客户端ID不能超过 128 个字符")
    String clientId,

    @Schema(description = "客户端密钥(留空=保持原值，服务端加密存储)")
    @Size(max = 512, message = "客户端密钥不能超过 512 个字符")
    String clientSecret,

    @Schema(description = "授权地址")
    @Size(max = 512, message = "授权地址不能超过 512 个字符")
    String authorizeUrl,

    @Schema(description = "令牌地址")
    @Size(max = 512, message = "令牌地址不能超过 512 个字符")
    String tokenUrl,

    @Schema(description = "用户信息地址")
    @Size(max = 512, message = "用户信息地址不能超过 512 个字符")
    String userinfoUrl,

    @Schema(description = "授权范围(多个用空格分隔)")
    @Size(max = 255, message = "授权范围不能超过 255 个字符")
    String scope,

    @Schema(description = "令牌请求客户端认证方式(post=表单,basic=HTTP Basic)")
    @Size(max = 16, message = "认证方式不能超过 16 个字符")
    String tokenAuthMethod,

    @Schema(description = "回调地址(需先在渠道后台登记，与登记值一字不差)")
    @Size(max = 512, message = "回调地址不能超过 512 个字符")
    String redirectUri,

    @Schema(description = "渠道扩展配置JSON(用户字段映射/webBaseUrl)")
    @Size(max = 4000, message = "扩展配置不能超过 4000 个字符")
    String extraConfig,

    @Schema(description = "备注")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    String remark
) {
}
