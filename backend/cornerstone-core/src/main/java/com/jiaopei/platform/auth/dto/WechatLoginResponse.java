package com.jiaopei.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "微信小程序登录响应")
public record WechatLoginResponse(
    @Schema(description = "微信openid是否已注册系统用户(true=已注册可直接使用令牌,false=需先调用注册接口选择身份)")
    Boolean registered,

    @Schema(description = "已注册时的登录响应(含访问令牌),未注册时为空")
    LoginResponse auth
) {
}
