package com.cornerstone.module.education.miniapp.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.cornerstone.core.auth.dto.InvitationPrecheckView;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.module.education.announcement.service.AnnouncementService;
import com.cornerstone.module.education.miniapp.service.MiniappQrcodeService;
import java.time.Duration;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 小程序码生成实现：邀请码直达注册页、公告直达公告详情页。
 *
 * <p>微信 wxacode.getUnlimited 同一个 scene 生成的图片内容固定不变，
 * 因此按 scene 缓存到 Redis（Base64 存 PNG），避免重复消耗微信接口配额。
 * 凭证被停用、公告被撤回后缓存不会立即失效，但扫码进页面后接口仍会拒绝，
 * 图片本身只是入口、不携带访问资格。</p>
 */
@Slf4j
@Service
public class MiniappQrcodeServiceImpl implements MiniappQrcodeService {

    /** 注册页路径，可用系统参数 miniapp.register.page 覆盖（如页面改版时） */
    private static final String DEFAULT_REGISTER_PAGE = "pages/register/index";

    /** 公告详情页路径，可用系统参数 miniapp.announcement.page 覆盖（如页面改版时） */
    private static final String DEFAULT_ANNOUNCEMENT_PAGE = "pages/messages/detail";

    /**
     * 公告 scene 前缀：小程序码的 scene 是整包字符串、页面自己解析，加前缀才能和邀请码
     * 共用同一份缓存键与同一个落地判断（邀请码编码不含等号，不会与 {@code a=数字} 撞车）。
     */
    private static final String ANNOUNCEMENT_SCENE_PREFIX = "a=";

    /** 微信 scene 参数的硬上限 */
    private static final int SCENE_MAX_LENGTH = 32;

    /** 小程序码缓存时间：码图与 scene 一一对应且不可变，缓存只为省接口配额 */
    private static final Duration CACHE_TTL = Duration.ofDays(30);

    private final WxMaService wxMaService;
    private final AuthInvitationService invitationService;
    private final AnnouncementService announcementService;
    private final SystemConfigService systemConfigService;
    private final StringRedisTemplate stringRedisTemplate;

    public MiniappQrcodeServiceImpl(WxMaService wxMaService,
                                    AuthInvitationService invitationService,
                                    AnnouncementService announcementService,
                                    SystemConfigService systemConfigService,
                                    StringRedisTemplate stringRedisTemplate) {
        this.wxMaService = wxMaService;
        this.invitationService = invitationService;
        this.announcementService = announcementService;
        this.systemConfigService = systemConfigService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public byte[] createInviteWxacode(String code) {
        String scene = normalizeCode(code);
        InvitationPrecheckView view = invitationService.precheck(scene);
        if (!view.valid()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                view.message() != null ? view.message() : "凭证不可用");
        }
        if (!AuthInvitationService.KIND_INVITE.equals(view.kind())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅邀请码支持生成注册小程序码");
        }
        return loadWxacode(scene,
            systemConfigService.getConfigValue("miniapp.register.page", DEFAULT_REGISTER_PAGE));
    }

    @Override
    public byte[] createAnnouncementWxacode(Long announcementId) {
        if (announcementId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "公告ID不能为空");
        }
        // 归属机构与"只有已发布可分享"都归公告服务判：草稿没有接收人行、撤回后扫码看不到内容，两种都不该出图
        announcementService.requireShareable(announcementId);
        return loadWxacode(ANNOUNCEMENT_SCENE_PREFIX + announcementId,
            systemConfigService.getConfigValue("miniapp.announcement.page", DEFAULT_ANNOUNCEMENT_PAGE));
    }

    /** 取码图：同一 scene 命中缓存就不再打微信接口 */
    private byte[] loadWxacode(String scene, String page) {
        String cacheKey = cacheKey(scene);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Base64.getDecoder().decode(cached);
        }

        byte[] png;
        try {
            // checkPath=false:允许在目标页发布前扫码联调;envVersion 不传默认 release
            png = wxMaService.getQrcodeService().createWxaCodeUnlimitBytes(
                scene, page, false, null, 280, false, null, false);
        } catch (WxErrorException e) {
            log.error("生成小程序码失败, scene={}, page={}", scene, page, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成小程序码失败：" + e.getMessage());
        }
        stringRedisTemplate.opsForValue().set(cacheKey, Base64.getEncoder().encodeToString(png), CACHE_TTL);
        return png;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邀请码不能为空");
        }
        String trimmed = code.trim();
        if (trimmed.length() > SCENE_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邀请码超过微信 scene 上限 32 字符");
        }
        return trimmed;
    }

    private String cacheKey(String scene) {
        return "cornerstone:wx:wxacode:" + wxMaService.getWxMaConfig().getAppid() + ":" + scene;
    }
}
