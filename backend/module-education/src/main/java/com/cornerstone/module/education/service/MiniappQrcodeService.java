package com.cornerstone.module.education.service;

/**
 * 小程序码（太阳码）生成服务。
 *
 * <p>供管理端把邀请码做成可扫码直达小程序注册页的图片：扫码后 scene 携带凭证编码，
 * 注册页据此自动预检并进入注册流程。</p>
 */
public interface MiniappQrcodeService {

    /**
     * 为指定邀请码生成小程序码 PNG。
     *
     * @param code 邀请码编码（微信 scene 上限 32 字符）
     * @return PNG 图片字节流
     */
    byte[] createInviteWxacode(String code);
}
