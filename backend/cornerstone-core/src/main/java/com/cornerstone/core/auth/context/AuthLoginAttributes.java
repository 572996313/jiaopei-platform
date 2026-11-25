package com.cornerstone.core.auth.context;

/**
 * 登录请求上的审计属性键。登录接口把实际使用的渠道写进请求属性,
 * 操作留痕切面读取后落到 oper_log.login_provider,便于区分账号密码/微信/第三方登录。
 */
public final class AuthLoginAttributes {

    /** 登录渠道编码(如 password、wechat_miniapp、linuxdo) */
    public static final String PROVIDER = "auth.login.provider";

    private AuthLoginAttributes() {
    }
}
