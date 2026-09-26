package com.cornerstone.module.education.service;

/**
 * 小程序码生成服务。
 *
 * <p>把管理端的对象做成可扫码直达小程序某个页面的图片：扫码后 scene 携带业务标识，
 * 目标页据此自动定位内容。目前两类入口——邀请码直达注册页、公告直达公告详情页。</p>
 */
public interface MiniappQrcodeService {

    /**
     * 为指定邀请码生成小程序码 PNG。
     *
     * @param code 邀请码编码（微信 scene 上限 32 字符）
     * @return PNG 图片字节流
     */
    byte[] createInviteWxacode(String code);

    /**
     * 为指定公告生成小程序码 PNG，扫码直达小程序公告详情页。
     *
     * @param announcementId 公告 ID（必须是当前登录机构已发布的公告）
     * @return PNG 图片字节流
     */
    byte[] createAnnouncementWxacode(Long announcementId);
}
