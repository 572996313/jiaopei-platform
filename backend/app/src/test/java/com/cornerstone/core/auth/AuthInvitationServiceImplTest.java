package com.cornerstone.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cornerstone.core.auth.domain.AuthInvitationEntity;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.dto.AccountActivateRequest;
import com.cornerstone.core.auth.dto.ActivationCodeCreateRequest;
import com.cornerstone.core.auth.dto.InvitationAccountSpec;
import com.cornerstone.core.auth.dto.InvitationCreateRequest;
import com.cornerstone.core.auth.dto.InvitationPrecheckView;
import com.cornerstone.core.auth.dto.InvitationRegisterRequest;
import com.cornerstone.core.auth.dto.InvitationView;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.event.UserRegisteredEvent;
import com.cornerstone.core.auth.mapper.AuthInvitationMapper;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.security.UserAccessTokenService;
import com.cornerstone.core.auth.service.PasswordValidator;
import com.cornerstone.core.auth.service.impl.AuthInvitationServiceImpl;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthInvitationServiceImplTest {

    private static final String INVITE_CODE = "INVABCDEFGHJKLMN";
    private static final String ACTIVATION_CODE = "ACTABCDEFGHJKLMN";

    private final AuthInvitationMapper invitationMapper = mock(AuthInvitationMapper.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final UserRoleRelMapper userRoleRelMapper = mock(UserRoleRelMapper.class);
    private final InstitutionMapper institutionMapper = mock(InstitutionMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PasswordValidator passwordValidator = mock(PasswordValidator.class);
    private final UserAccessTokenService userAccessTokenService = mock(UserAccessTokenService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private AuthInvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthInvitationServiceImpl(invitationMapper, userAccountMapper, roleMapper,
            userRoleRelMapper, institutionMapper, passwordEncoder, passwordValidator,
            userAccessTokenService, eventPublisher);
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}encoded");
        when(userAccessTokenService.issue(any())).thenReturn(loginResponse());
    }

    @Test
    void createInviteGeneratesInviteCodeAndValidatesRole() {
        when(institutionMapper.selectById(1L)).thenReturn(institution(1L, "示范机构"));
        when(roleMapper.selectOne(any())).thenReturn(role(9L, "STAFF"));

        InvitationView view = service.createInvite(
            new InvitationCreateRequest(1L, "STAFF", 5, 30, "开业邀请"));

        assertTrue(view.code().startsWith("INV"));
        assertEquals("INVITE", view.kind());
        assertEquals("STAFF", view.roleCode());
        ArgumentCaptor<AuthInvitationEntity> captor = ArgumentCaptor.forClass(AuthInvitationEntity.class);
        verify(invitationMapper).insert(captor.capture());
        assertEquals(5, captor.getValue().getMaxUses());
        assertNotNull(captor.getValue().getExpiresAt());
    }

    @Test
    void createInviteRejectsUnknownRole() {
        when(institutionMapper.selectById(1L)).thenReturn(institution(1L, "示范机构"));
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.createInvite(
            new InvitationCreateRequest(1L, "GHOST", null, null, null)));
        verify(invitationMapper, never()).insert(any(AuthInvitationEntity.class));
    }

    @Test
    void activationCodeRequestIsIdempotentForPendingUser() {
        when(userAccountMapper.selectById(50L)).thenReturn(pendingUser(50L));
        when(invitationMapper.selectList(any()))
            .thenReturn(List.of(usableInvitation(ACTIVATION_CODE, "ACTIVATE", 50L, null)));

        InvitationView view = service.createActivationCode(new ActivationCodeCreateRequest(50L, null));

        assertEquals(ACTIVATION_CODE, view.code());
        verify(invitationMapper, never()).insert(any(AuthInvitationEntity.class));
    }

    @Test
    void activationCodeRejectedForDisabledUser() {
        UserAccountEntity disabled = pendingUser(50L);
        disabled.setStatus("disabled");
        when(userAccountMapper.selectById(50L)).thenReturn(disabled);

        assertThrows(BusinessException.class,
            () -> service.createActivationCode(new ActivationCodeCreateRequest(50L, null)));
    }

    @Test
    void registerCreatesUserAttachesRoleConsumesCodeAndPublishesEvent() {
        when(invitationMapper.selectOne(any()))
            .thenReturn(usableInvitation(INVITE_CODE, "INVITE", null, "STAFF"));
        when(userAccountMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectOne(any())).thenReturn(role(9L, "STAFF"));
        when(invitationMapper.tryConsume(anyLong())).thenReturn(1);
        when(userAccountMapper.insert(any(UserAccountEntity.class))).thenAnswer(call -> {
            ((UserAccountEntity) call.getArgument(0)).setId(77L);
            return 1;
        });

        LoginResponse response = service.register(new InvitationRegisterRequest(
            INVITE_CODE, "zhangsan", "张三", "13800000000", "Passw0rd!"));

        assertNotNull(response);
        ArgumentCaptor<UserAccountEntity> userCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountMapper).insert(userCaptor.capture());
        UserAccountEntity created = userCaptor.getValue();
        assertEquals("INVITATION_CODE", created.getRegisterSource());
        assertEquals("active", created.getStatus());
        assertFalse(created.getForceChangePassword());
        verify(userRoleRelMapper).insert(any(com.cornerstone.core.auth.domain.UserRoleRelEntity.class));
        verify(invitationMapper).tryConsume(anyLong());
        verify(eventPublisher).publishEvent(new UserRegisteredEvent(77L, 1L, "INVITE", "STAFF", 300L));
    }

    @Test
    void registerRejectsDuplicateUsernameBeforeConsumingCode() {
        when(invitationMapper.selectOne(any()))
            .thenReturn(usableInvitation(INVITE_CODE, "INVITE", null, "STAFF"));
        when(userAccountMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.register(new InvitationRegisterRequest(
            INVITE_CODE, "taken", "张三", null, "Passw0rd!")));
        verify(invitationMapper, never()).tryConsume(anyLong());
    }

    @Test
    void registerAbortsWhenConcurrentConsumeFails() {
        when(invitationMapper.selectOne(any()))
            .thenReturn(usableInvitation(INVITE_CODE, "INVITE", null, "STAFF"));
        when(userAccountMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectOne(any())).thenReturn(role(9L, "STAFF"));
        when(invitationMapper.tryConsume(anyLong())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.register(new InvitationRegisterRequest(
            INVITE_CODE, "zhangsan", "张三", null, "Passw0rd!")));
        verify(userAccountMapper, never()).insert(any(UserAccountEntity.class));
    }

    @Test
    void registerRejectsWrongKindAndExpiredCode() {
        when(invitationMapper.selectOne(any()))
            .thenReturn(usableInvitation(ACTIVATION_CODE, "ACTIVATE", 50L, null));
        BusinessException kindError = assertThrows(BusinessException.class, () -> service.register(
            new InvitationRegisterRequest(ACTIVATION_CODE, "zhangsan", "张三", null, "Passw0rd!")));
        assertTrue(kindError.getMessage().contains("激活码"));

        AuthInvitationEntity expired = usableInvitation(INVITE_CODE, "INVITE", null, "STAFF");
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(invitationMapper.selectOne(any())).thenReturn(expired);
        BusinessException expiredError = assertThrows(BusinessException.class, () -> service.register(
            new InvitationRegisterRequest(INVITE_CODE, "zhangsan", "张三", null, "Passw0rd!")));
        assertTrue(expiredError.getMessage().contains("已过期"));
    }

    @Test
    void registerInvitedAccountCreatesPasswordlessUserAndReturnsEntityWithoutIssuingToken() {
        when(invitationMapper.selectOne(any()))
            .thenReturn(usableInvitation(INVITE_CODE, "INVITE", null, "STAFF"));
        when(userAccountMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectOne(any())).thenReturn(role(9L, "STAFF"));
        when(invitationMapper.tryConsume(anyLong())).thenReturn(1);
        when(userAccountMapper.insert(any(UserAccountEntity.class))).thenAnswer(call -> {
            ((UserAccountEntity) call.getArgument(0)).setId(88L);
            return 1;
        });

        UserAccountEntity user = service.registerInvitedAccount(new InvitationAccountSpec(
            INVITE_CODE, "wx_openid_123", "微信用户", null, "WECHAT_MINIAPP"));

        assertNotNull(user);
        assertNull(user.getPasswordHash());
        assertNull(user.getPasswordUpdatedAt());
        assertEquals("WECHAT_MINIAPP", user.getRegisterSource());
        assertEquals("active", user.getStatus());
        assertFalse(user.getForceChangePassword());
        // 底座不签令牌,由业务自行签发
        verify(userAccessTokenService, never()).issue(any());
        // 无密码建号不触发密码策略校验
        verify(passwordValidator, never()).validate(any());
        verify(invitationMapper).tryConsume(anyLong());
        verify(eventPublisher).publishEvent(new UserRegisteredEvent(88L, 1L, "INVITE", "STAFF", 300L));
    }

    @Test
    void registerInvitedAccountRejectsBlankUsernameWithoutConsumingCode() {
        when(invitationMapper.selectOne(any()))
            .thenReturn(usableInvitation(INVITE_CODE, "INVITE", null, "STAFF"));

        assertThrows(BusinessException.class, () -> service.registerInvitedAccount(
            new InvitationAccountSpec(INVITE_CODE, "  ", "微信用户", null, "WECHAT_MINIAPP")));
        verify(invitationMapper, never()).tryConsume(anyLong());
    }

    @Test
    void activateSetsPasswordAndEnablesPendingUser() {
        when(invitationMapper.selectOne(any()))
            .thenReturn(usableInvitation(ACTIVATION_CODE, "ACTIVATE", 50L, null));
        when(invitationMapper.tryConsume(anyLong())).thenReturn(1);
        UserAccountEntity pending = pendingUser(50L);
        when(userAccountMapper.selectById(50L)).thenReturn(pending);

        LoginResponse response = service.activate(
            new AccountActivateRequest(ACTIVATION_CODE, "Passw0rd!"));

        assertNotNull(response);
        assertEquals("{bcrypt}encoded", pending.getPasswordHash());
        assertEquals("active", pending.getStatus());
        assertFalse(pending.getForceChangePassword());
        verify(userAccountMapper).updateById(pending);
        verify(eventPublisher).publishEvent(new UserRegisteredEvent(50L, 1L, "ACTIVATE", null, 300L));
    }

    @Test
    void activateRejectsAlreadyActiveUser() {
        when(invitationMapper.selectOne(any()))
            .thenReturn(usableInvitation(ACTIVATION_CODE, "ACTIVATE", 50L, null));
        UserAccountEntity active = pendingUser(50L);
        active.setStatus("active");
        when(userAccountMapper.selectById(50L)).thenReturn(active);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.activate(new AccountActivateRequest(ACTIVATION_CODE, "Passw0rd!")));
        assertTrue(exception.getMessage().contains("已激活"));
        verify(invitationMapper, never()).tryConsume(anyLong());
    }

    @Test
    void precheckReportsInvalidCodeWithoutThrowing() {
        when(invitationMapper.selectOne(any())).thenReturn(null);

        InvitationPrecheckView view = service.precheck("INVNOPE");

        assertFalse(view.valid());
        assertNotNull(view.message());
        assertNull(view.kind());
    }

    @Test
    void precheckReturnsInstitutionAndRoleForUsableInvite() {
        when(invitationMapper.selectOne(any()))
            .thenReturn(usableInvitation(INVITE_CODE, "INVITE", null, "STAFF"));
        when(institutionMapper.selectById(1L)).thenReturn(institution(1L, "示范机构"));
        when(roleMapper.selectOne(any())).thenReturn(role(9L, "STAFF"));

        InvitationPrecheckView view = service.precheck(INVITE_CODE);

        assertTrue(view.valid());
        assertEquals("INVITE", view.kind());
        assertEquals("示范机构", view.institutionName());
        assertEquals("职员", view.roleName());
    }

    // ------------------------------------------------------------------ 构造工具

    private AuthInvitationEntity usableInvitation(String code, String kind, Long userId, String roleCode) {
        AuthInvitationEntity entity = new AuthInvitationEntity();
        entity.setId(300L);
        entity.setInstitutionId(1L);
        entity.setCode(code);
        entity.setKind(kind);
        entity.setUserId(userId);
        entity.setRoleCode(roleCode);
        entity.setMaxUses(1);
        entity.setUsedCount(0);
        entity.setStatus("active");
        entity.setDeleted(Boolean.FALSE);
        return entity;
    }

    private UserAccountEntity pendingUser(Long id) {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(id);
        user.setInstitutionId(1L);
        user.setUsername("pre_" + id);
        user.setDisplayName("预建账号");
        user.setStatus("pending_activation");
        user.setDeleted(Boolean.FALSE);
        return user;
    }

    private RoleEntity role(Long id, String code) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName("职员");
        role.setStatus("active");
        role.setDeleted(Boolean.FALSE);
        return role;
    }

    private InstitutionEntity institution(Long id, String name) {
        InstitutionEntity institution = new InstitutionEntity();
        institution.setId(id);
        institution.setOrgName(name);
        institution.setDeleted(Boolean.FALSE);
        return institution;
    }

    private LoginResponse loginResponse() {
        return new LoginResponse(77L, 1L, "zhangsan", "张三",
            List.of(), List.of(), "Bearer", "token", false, "admin");
    }
}
