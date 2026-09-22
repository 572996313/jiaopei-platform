package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.binarywang.wx.miniapp.api.WxMaQrcodeService;
import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.config.WxMaConfig;
import com.cornerstone.core.auth.dto.InvitationPrecheckView;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.module.education.service.impl.MiniappQrcodeServiceImpl;
import java.time.Duration;
import java.util.Base64;
import me.chanjar.weixin.common.error.WxErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MiniappQrcodeServiceImplTest {

    private static final String CODE = "INVABCDEFGHIJ";
    private static final byte[] PNG = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private final WxMaService wxMaService = mock(WxMaService.class);
    private final WxMaQrcodeService qrcodeService = mock(WxMaQrcodeService.class);
    private final WxMaConfig wxMaConfig = mock(WxMaConfig.class);
    private final AuthInvitationService invitationService = mock(AuthInvitationService.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private MiniappQrcodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MiniappQrcodeServiceImpl(wxMaService, invitationService, systemConfigService, redisTemplate);
        when(wxMaService.getQrcodeService()).thenReturn(qrcodeService);
        when(wxMaService.getWxMaConfig()).thenReturn(wxMaConfig);
        when(wxMaConfig.getAppid()).thenReturn("wx1370a4fd669d14d8");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(systemConfigService.getConfigValue("miniapp.register.page", "pages/register/index"))
            .thenReturn("pages/register/index");
    }

    @Test
    void generatesWxacodeAndWritesCache() throws WxErrorException {
        when(invitationService.precheck(CODE)).thenReturn(precheck(true, "INVITE", null));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(qrcodeService.createWxaCodeUnlimitBytes(eq(CODE), eq("pages/register/index"),
            anyBoolean(), isNull(), anyInt(), anyBoolean(), isNull(), anyBoolean())).thenReturn(PNG);

        byte[] result = service.createInviteWxacode(CODE);

        assertArrayEquals(PNG, result);
        verify(qrcodeService).createWxaCodeUnlimitBytes(eq(CODE), eq("pages/register/index"),
            eq(false), isNull(), eq(280), eq(false), isNull(), eq(false));
        verify(valueOperations).set(eq("cornerstone:wx:wxacode:wx1370a4fd669d14d8:" + CODE),
            eq(Base64.getEncoder().encodeToString(PNG)), eq(Duration.ofDays(30)));
    }

    @Test
    void returnsCachedPngWithoutCallingWechat() throws WxErrorException {
        when(invitationService.precheck(CODE)).thenReturn(precheck(true, "INVITE", null));
        when(valueOperations.get(anyString())).thenReturn(Base64.getEncoder().encodeToString(PNG));

        byte[] result = service.createInviteWxacode("  " + CODE + " ");

        assertArrayEquals(PNG, result);
        verify(qrcodeService, never()).createWxaCodeUnlimitBytes(any(), any(), anyBoolean(),
            any(), anyInt(), anyBoolean(), any(), anyBoolean());
    }

    @Test
    void rejectsInvalidOrActivateKindCode() {
        when(invitationService.precheck(CODE)).thenReturn(precheck(false, null, "凭证已过期"));
        BusinessException expired = assertThrows(BusinessException.class,
            () -> service.createInviteWxacode(CODE));
        assertTrue(expired.getMessage().contains("已过期"));

        when(invitationService.precheck(CODE)).thenReturn(precheck(true, "ACTIVATE", null));
        BusinessException kind = assertThrows(BusinessException.class,
            () -> service.createInviteWxacode(CODE));
        assertTrue(kind.getMessage().contains("邀请码"));
    }

    @Test
    void wrapsWechatApiFailure() throws WxErrorException {
        when(invitationService.precheck(CODE)).thenReturn(precheck(true, "INVITE", null));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(qrcodeService.createWxaCodeUnlimitBytes(anyString(), anyString(), anyBoolean(),
            any(), anyInt(), anyBoolean(), any(), anyBoolean()))
            .thenThrow(new WxErrorException("api frequency limit"));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.createInviteWxacode(CODE));
        assertTrue(exception.getMessage().contains("生成小程序码失败"));
    }

    private InvitationPrecheckView precheck(boolean valid, String kind, String message) {
        return new InvitationPrecheckView(valid, kind, null, null, null, null, message);
    }
}
