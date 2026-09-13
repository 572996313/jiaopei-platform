package com.jiaopei.platform.auth.service;

import com.jiaopei.platform.auth.dto.LoginResponse;
import com.jiaopei.platform.auth.dto.WechatBindRequest;
import com.jiaopei.platform.auth.dto.WechatLoginRequest;
import com.jiaopei.platform.auth.dto.WechatLoginResponse;

/**
 * 微信小程序认证:凭 wx.login 的 code 完成登录或账号绑定,令牌体系与账号密码登录一致。
 */
public interface WechatAuthService {

    WechatLoginResponse login(WechatLoginRequest request);

    LoginResponse bind(WechatBindRequest request);
}
