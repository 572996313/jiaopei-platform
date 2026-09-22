package com.cornerstone.module.education.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.cornerstone.core.auth.dto.InvitationPrecheckView;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.module.education.service.MiniappQrcodeService;
import java.time.Duration;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 邀请码 → 小程序码生成实现。
 *
 * <p>微信 wxacode.getUnlimited 同一个 scene 生成的图片内容固定不变，
 * 因此按 scene 缓存到 Redis（Base64 存 PNG），避免重复消耗微信接口配额。
 * 凭证被停用后缓存不会立即失效，但扫码进注册页后注册接口仍会预检拒绝，
 * 图片本身只是入口、不携带注册资格。</p>
 */
@Slf4j
@Service
public class MiniappQrcodeServiceImpl implements MiniappQrcodeService {

    /** 注册页路径，可用系统参数 miniapp.register.page 覆盖（如页面改版时） */
    private static final String DEFAULT_REGISTER_PAGE = "pages/register/index";

    /** 微信 scene 参数的硬上限 */
    private static final int SCENE_MAX_LENGTH = 32;

    /** 小程序码缓存时间：码图与凭证编码一一对应且不可变，缓存只为省接口配额 */
    private static final Duration CACHE_TTL = Duration.ofDays(30);

    private final WxMaService wxMaService;
    private final AuthInvitationService invitationService;
    private final SystemConfigService systemConfigService;
    private final StringRedisTemplate stringRedisTemplate;

    public MiniappQrcodeServiceImpl(WxMaService wxMaService,
                                    AuthInvitationService invitationService,
                                    SystemConfigService systemConfigService,
                                    StringRedisTemplate stringRedisTemplate) {
        this.wxMaService = wxMaService;
        this.invitationService = invitationService;
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

        String cacheKey = cacheKey(scene);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Base64.getDecoder().decode(cached);
        }

        String page = systemConfigService.getConfigValue("miniapp.register.page", DEFAULT_REGISTER_PAGE);
        byte[] png;
        try {
            // checkPath=false:允许在注册页发布前扫码联调;envVersion 不传默认 release
            png = wxMaService.getQrcodeService().createWxaCodeUnlimitBytes(
                scene, page, false, null, 280, false, null, false);
        } catch (WxErrorException e) {
            log.error("生成邀请码小程序码失败, code={}", scene, e);
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
