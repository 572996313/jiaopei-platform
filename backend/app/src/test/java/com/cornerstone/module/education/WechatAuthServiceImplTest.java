package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.WxMaUserService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.config.WxMaConfig;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserIdentityEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.AccountOptionView;
import com.cornerstone.core.auth.dto.InvitationAccountSpec;
import com.cornerstone.core.auth.dto.InvitationPrecheckView;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.event.UserAccountDeregisteredEvent;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.security.UserAccessTokenService;
import com.cornerstone.core.auth.service.AccountCandidateService;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.auth.service.ThirdPartyLoginService;
import com.cornerstone.core.auth.service.TokenCacheService;
import com.cornerstone.core.auth.service.UserIdentityService;
import com.cornerstone.core.auth.social.ExternalIdentity;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.module.education.dto.WechatLoginRequest;
import com.cornerstone.module.education.dto.WechatLoginResponse;
import com.cornerstone.module.education.dto.WechatRegisterRequest;
import com.cornerstone.module.education.service.impl.WechatAuthServiceImpl;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class WechatAuthServiceImplTest {

    private static final String OPENID = "oABC-def_123";
    private static final String UNION_ID = "union-1";
    private static final String LOGIN_CODE = "081Kk0ll2xxxRealCode";

    private final WxMaService wxMaService = mock(WxMaService.class);
    private final WxMaConfig wxMaConfig = mock(WxMaConfig.class);
    private final WxMaUserService wxMaUserService = mock(WxMaUserService.class);
    private final UserIdentityService userIdentityService = mock(UserIdentityService.class);
    private final AccountCandidateService accountCandidateService = mock(AccountCandidateService.class);
    private final ThirdPartyLoginService thirdPartyLoginService = mock(ThirdPartyLoginService.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final UserRoleRelMapper userRoleRelMapper = mock(UserRoleRelMapper.class);
    private final UserAccessTokenService userAccessTokenService = mock(UserAccessTokenService.class);
    private final TokenCacheService tokenCacheService = mock(TokenCacheService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final AuthInvitationService invitationService = mock(AuthInvitationService.class);

    private WechatAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WechatAuthServiceImpl(wxMaService, userIdentityService, accountCandidateService,
            thirdPartyLoginService, userAccountMapper,
            roleMapper, userRoleRelMapper,
            userAccessTokenService, tokenCacheService, systemConfigService, invitationService, eventPublisher);
        when(wxMaService.getWxMaConfig()).thenReturn(wxMaConfig);
        when(wxMaConfig.getAppid()).thenReturn("wx1370a4fd669d14d8");
        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(systemConfigService.getIntConfig("auth.register.default_institution_id", 1)).thenReturn(1);
        when(userAccessTokenService.issue(any())).thenReturn(loginResponse());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginReturnsRegisteredFalseWhenOpenidNotRegistered() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of());

        WechatLoginResponse response = service.login(new WechatLoginRequest(LOGIN_CODE));

        assertFalse(response.registered());
        assertNull(response.auth());
        assertNull(response.ticket());
        assertTrue(response.accounts().isEmpty());
        verify(userAccessTokenService, never()).issue(any());
    }

    @Test
    void loginIssuesTokenDirectlyWhenSingleAccountBound() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of(identity(7L)));
        when(accountCandidateService.activeUserIds(List.of(7L))).thenReturn(List.of(7L));
        when(userAccountMapper.selectById(7L)).thenReturn(activeUser(7L));

        WechatLoginResponse response = service.login(new WechatLoginRequest(LOGIN_CODE));

        assertTrue(response.registered());
        assertNotNull(response.auth());
        assertNull(response.ticket());
        verify(userIdentityService).touch(any(ExternalIdentity.class));
    }

    @Test
    void loginReturnsAccountOptionsWhenMultipleAccountsBound() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of(identity(7L), identity(8L)));
        when(accountCandidateService.activeUserIds(List.of(7L, 8L))).thenReturn(List.of(7L, 8L));
        when(accountCandidateService.build(List.of(7L, 8L))).thenReturn(List.of(
            new AccountOptionView("0", "张小明", "parent", "示范机构"),
            new AccountOptionView("1", "张老师", "admin", "示范机构")));
        when(thirdPartyLoginService.createSelectTicket(any(ExternalIdentity.class), eq(List.of(7L, 8L))))
            .thenReturn("select-ticket");

        WechatLoginResponse response = service.login(new WechatLoginRequest(LOGIN_CODE));

        assertTrue(response.registered());
        assertNull(response.auth());
        assertEquals("select-ticket", response.ticket());
        assertEquals(2, response.accounts().size());
        assertEquals("admin", response.accounts().get(1).portal());
        verify(userAccessTokenService, never()).issue(any());
    }

    @Test
    void loginRejectsWhenAllBoundAccountsDisabled() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of(identity(7L)));
        when(accountCandidateService.activeUserIds(List.of(7L))).thenReturn(List.of());

        assertThrows(org.springframework.security.authentication.DisabledException.class,
            () -> service.login(new WechatLoginRequest(LOGIN_CODE)));
        verify(userAccessTokenService, never()).issue(any());
    }

    @Test
    void selectDelegatesToMultiAccountTicket() {
        when(thirdPartyLoginService.selectAccount("ticket", "1", false))
            .thenReturn(new ThirdPartyLoginService.LoginResult(loginResponse(), "wechat_miniapp"));

        LoginResponse response = service.select("ticket", "1");

        assertEquals("access-token", response.accessToken());
        verify(thirdPartyLoginService).selectAccount("ticket", "1", false);
    }

    @Test
    void loginRejectsMockCodeFromDevtools() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.login(new WechatLoginRequest("the code is a mock one")));

        assertTrue(exception.getMessage().contains("登录凭证无效"));
    }

    @Test
    void registerCreatesUserWithRoleAndBinding() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of());
        when(roleMapper.selectOne(any())).thenReturn(role(101L, "MINIAPP_STUDENT"));
        when(userAccountMapper.insert(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, UserAccountEntity.class).setId(9L);
            return 1;
        });

        service.register(new WechatRegisterRequest(LOGIN_CODE, "STUDENT", null, "小明"));

        ArgumentCaptor<UserAccountEntity> userCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountMapper).insert(userCaptor.capture());
        UserAccountEntity created = userCaptor.getValue();
        assertEquals("wx_" + OPENID, created.getUsername());
        assertNull(created.getPasswordHash());
        assertEquals("小明", created.getDisplayName());
        assertEquals("WECHAT_MINIAPP", created.getRegisterSource());
        assertEquals(Long.valueOf(1L), created.getInstitutionId());
        assertEquals("active", created.getStatus());
        assertFalse(created.getForceChangePassword());

        ArgumentCaptor<UserRoleRelEntity> relCaptor = ArgumentCaptor.forClass(UserRoleRelEntity.class);
        verify(userRoleRelMapper).insert(relCaptor.capture());
        assertEquals(Long.valueOf(9L), relCaptor.getValue().getUserId());
        assertEquals(Long.valueOf(101L), relCaptor.getValue().getRoleId());

        ArgumentCaptor<ExternalIdentity> identityCaptor = ArgumentCaptor.forClass(ExternalIdentity.class);
        verify(userIdentityService).bind(identityCaptor.capture(), eq(9L));
        assertEquals("wechat_miniapp", identityCaptor.getValue().providerCode());
        assertEquals("miniapp", identityCaptor.getValue().appType());
        assertEquals(OPENID, identityCaptor.getValue().openId());
        assertEquals("小明", identityCaptor.getValue().nickname());
    }

    @Test
    void registerMapsParentRoleAndFallsBackToDefaultDisplayName() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of());
        when(roleMapper.selectOne(any())).thenReturn(role(102L, "MINIAPP_PARENT"));
        when(userAccountMapper.insert(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, UserAccountEntity.class).setId(10L);
            return 1;
        });

        service.register(new WechatRegisterRequest(LOGIN_CODE, "PARENT", null, "  "));

        ArgumentCaptor<UserAccountEntity> userCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountMapper).insert(userCaptor.capture());
        assertEquals("微信用户", userCaptor.getValue().getDisplayName());
        ArgumentCaptor<UserRoleRelEntity> relCaptor = ArgumentCaptor.forClass(UserRoleRelEntity.class);
        verify(userRoleRelMapper).insert(relCaptor.capture());
        assertEquals(Long.valueOf(102L), relCaptor.getValue().getRoleId());
    }

    @Test
    void registerIsIdempotentForRegisteredOpenid() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of(identity(7L)));
        when(accountCandidateService.activeUserIds(List.of(7L))).thenReturn(List.of(7L));
        when(userAccountMapper.selectById(7L)).thenReturn(activeUser(7L));

        service.register(new WechatRegisterRequest(LOGIN_CODE, "PARENT", null, "家长"));

        verify(userAccountMapper, never()).insert(any(UserAccountEntity.class));
        verify(userRoleRelMapper, never()).insert(any(UserRoleRelEntity.class));
        verify(userIdentityService, never()).bind(any(), anyLong());
        verify(userIdentityService).touch(any(ExternalIdentity.class));
    }

    @Test
    void registerWithoutInviteRejectsUnknownRoleAtServiceLayer() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.register(new WechatRegisterRequest(LOGIN_CODE, "TEACHER", null, "老师")));

        assertTrue(exception.getMessage().contains("STUDENT"));
        verify(userAccountMapper, never()).insert(any(UserAccountEntity.class));
    }

    @Test
    void registerWithInviteDelegatesToCoreAndCreatesBinding() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of());
        when(invitationService.precheck("INVABC"))
            .thenReturn(new InvitationPrecheckView(true, "INVITE", "示范机构", "MINIAPP_PARENT", "家长端用户", null, null));
        UserAccountEntity invited = activeUser(55L);
        when(invitationService.registerInvitedAccount(any())).thenReturn(invited);

        service.register(new WechatRegisterRequest(LOGIN_CODE, null, "INVABC", "家长"));

        ArgumentCaptor<InvitationAccountSpec> specCaptor = ArgumentCaptor.forClass(InvitationAccountSpec.class);
        verify(invitationService).registerInvitedAccount(specCaptor.capture());
        assertEquals("wx_" + OPENID, specCaptor.getValue().username());
        assertEquals("WECHAT_MINIAPP", specCaptor.getValue().registerSource());
        // 账号建档由底座原语完成,业务侧不再自己 insert
        verify(userAccountMapper, never()).insert(any(UserAccountEntity.class));
        verify(userIdentityService).bind(any(ExternalIdentity.class), eq(55L));
    }

    @Test
    void registerWithInviteRejectsNonMiniappRoleAndInvalidCode() {
        mockCode2Session();
        when(userIdentityService.findByExternal(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of());
        // 管理类角色邀请码不得进小程序注册通道
        when(invitationService.precheck("INVADMIN"))
            .thenReturn(new InvitationPrecheckView(true, "INVITE", "示范机构", "PLATFORM_ADMIN", "平台管理员", null, null));
        BusinessException forbidden = assertThrows(BusinessException.class,
            () -> service.register(new WechatRegisterRequest(LOGIN_CODE, null, "INVADMIN", "黑产")));
        assertTrue(forbidden.getMessage().contains("不支持小程序自助注册"));
        verify(invitationService, never()).registerInvitedAccount(any());

        // 无效/过期邀请码直接拒绝
        when(invitationService.precheck("INVBAD"))
            .thenReturn(new InvitationPrecheckView(false, null, null, null, null, null, "凭证已过期"));
        BusinessException invalid = assertThrows(BusinessException.class,
            () -> service.register(new WechatRegisterRequest(LOGIN_CODE, null, "INVBAD", "黑产")));
        assertTrue(invalid.getMessage().contains("已过期"));
    }

    @Test
    void bindAddsIdentityToCurrentAccount() {
        mockCode2Session();
        authenticate(7L, "ADMIN");
        when(userAccountMapper.selectById(7L)).thenReturn(activeUser(7L));

        service.bind(new WechatLoginRequest(LOGIN_CODE));

        verify(userIdentityService).bind(any(ExternalIdentity.class), eq(7L));
        verify(userIdentityService).touch(any(ExternalIdentity.class));
    }

    @Test
    void deregisterRemovesMiniappAccountData() {
        authenticate(7L, "WECHAT_MINIAPP");

        service.deregister();

        verify(tokenCacheService).invalidateUserTokens(7L);
        verify(userRoleRelMapper).physicalDeleteByUserId(7L);
        // 注销时发布事件，学员/教师档案归属、用户-学员关联与第三方身份绑定的清理由业务模块监听器完成
        verify(eventPublisher).publishEvent(new UserAccountDeregisteredEvent(7L));
        verify(userAccountMapper).physicalDeleteById(7L);
    }

    @Test
    void deregisterRejectsAdminCreatedAccount() {
        authenticate(1L, "ADMIN");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.deregister());

        assertTrue(exception.getMessage().contains("不支持自助注销"));
        verify(tokenCacheService, never()).invalidateUserTokens(any(Long.class));
        verify(userAccountMapper, never()).physicalDeleteById(any(Long.class));
    }

    private void authenticate(Long userId, String registerSource) {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(userId);
        user.setUsername("wx_" + userId);
        user.setStatus("active");
        user.setRegisterSource(registerSource);
        AuthenticatedUser authenticated = new AuthenticatedUser(
            userId, 1L, user.getUsername(), java.util.Set.of(), java.util.Set.of(), false, "admin");
        when(userAccountMapper.selectById(userId)).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(authenticated, "token", java.util.Set.of()));
    }

    private void mockCode2Session() {
        WxMaJscode2SessionResult session = mock(WxMaJscode2SessionResult.class);
        when(session.getOpenid()).thenReturn(OPENID);
        when(session.getUnionid()).thenReturn(UNION_ID);
        try {
            when(wxMaUserService.getSessionInfo(LOGIN_CODE)).thenReturn(session);
        } catch (Exception neverHappens) {
            throw new AssertionError(neverHappens);
        }
    }

    private UserIdentityEntity identity(Long userId) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setProviderCode("wechat_miniapp");
        identity.setAppType("miniapp");
        identity.setOpenId(OPENID);
        identity.setUnionId(UNION_ID);
        identity.setUserId(userId);
        return identity;
    }

    private RoleEntity role(Long id, String roleCode) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setStatus("active");
        return role;
    }

    private UserAccountEntity activeUser(Long userId) {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(userId);
        user.setUsername("wx_" + userId);
        user.setStatus("active");
        user.setRegisterSource("WECHAT_MINIAPP");
        return user;
    }

    private LoginResponse loginResponse() {
        return new LoginResponse(1L, 1L, "wx_" + OPENID, "微信用户",
            List.of("MINIAPP_STUDENT"), List.of("miniapp:access"),
            "Bearer", "access-token", false, "student");
    }
}
