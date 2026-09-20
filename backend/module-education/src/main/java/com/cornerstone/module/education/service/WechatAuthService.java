package com.cornerstone.module.education.service;

import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.module.education.dto.WechatLoginRequest;
import com.cornerstone.module.education.dto.WechatLoginResponse;
import com.cornerstone.module.education.dto.WechatRegisterRequest;

/**
 * 微信小程序认证:凭 wx.login 的 code 完成登录、自助注册与账号注销,令牌体系与账号密码登录一致。
 */
public interface WechatAuthService {

    WechatLoginResponse login(WechatLoginRequest request);

    LoginResponse register(WechatRegisterRequest request);

    void deregister();
}
