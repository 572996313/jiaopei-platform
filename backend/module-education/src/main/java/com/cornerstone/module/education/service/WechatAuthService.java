package com.cornerstone.module.education.service;

import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.module.education.dto.WechatLoginRequest;
import com.cornerstone.module.education.dto.WechatLoginResponse;
import com.cornerstone.module.education.dto.WechatRegisterRequest;

/**
 * 微信小程序认证:凭 wx.login 的 code 完成登录、多账号选择、自助注册、绑定与账号注销,
 * 令牌体系与账号密码登录一致。
 */
public interface WechatAuthService {

    WechatLoginResponse login(WechatLoginRequest request);

    /** 同一微信绑定多个账号时，用登录返回的一次性票据选定一个账号 */
    LoginResponse select(String ticket, String accountKey);

    LoginResponse register(WechatRegisterRequest request);

    /** 已登录账号补绑当前微信（员工账号在小程序里绑定微信后即可一键登录） */
    void bind(WechatLoginRequest request);

    void deregister();
}
