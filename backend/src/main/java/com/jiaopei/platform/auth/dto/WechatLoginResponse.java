package com.jiaopei.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "微信小程序登录响应")
public record WechatLoginResponse(
    @Schema(description = "微信openid是否已绑定系统用户(true=已绑定可直接使用令牌,false=需调用绑定接口)")
    Boolean bound,

    @Schema(description = "绑定成立时的登录响应(含访问令牌),未绑定时为空")
    LoginResponse auth
) {
}
