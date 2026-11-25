package com.cornerstone.module.education.dto;

import com.cornerstone.core.auth.dto.AccountOptionView;
import com.cornerstone.core.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "微信小程序登录响应")
public record WechatLoginResponse(
    @Schema(description = "微信openid是否已绑定系统用户(true=可直接登录或选账号,false=需先调用注册接口选择身份)")
    Boolean registered,

    @Schema(description = "只命中一个账号时的登录响应(含访问令牌);多账号或未注册时为空")
    LoginResponse auth,

    @Schema(description = "命中多个账号时的一次性选择票据;单账号/未注册时为空")
    String ticket,

    @Schema(description = "命中多个账号时的候选列表(显示名/所属端/机构);单账号/未注册时为空")
    List<AccountOptionView> accounts
) {
}
