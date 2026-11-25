package com.cornerstone.core.auth;

import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.security.UserAccessTokenService;
import com.cornerstone.core.auth.service.AccountCandidateService;
import com.cornerstone.core.auth.service.ThirdPartyLoginService;
import com.cornerstone.core.auth.service.UserIdentityService;
import com.cornerstone.core.auth.social.AuthProviderRegistry;
import com.cornerstone.core.auth.social.ExternalIdentity;
import com.cornerstone.core.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 票据一次性消费的约束：必须走 Lua GET+DEL，不能发 GETDEL——
 * 现场与本地都跑过 6.2 以下的 Redis，GETDEL 会直接报 unknown command。
 */
class ThirdPartyLoginServiceTest {

    private static final String SELECT_TICKET_JSON = """
        {"identity":{"providerCode":"wechat_miniapp","appType":"miniapp","openId":"o1",
         "unionId":null,"nickname":"张老师","avatarUrl":null},"userIds":[7]}
        """;

    private final AuthProviderRegistry providerRegistry = mock(AuthProviderRegistry.class);
    private final UserIdentityService userIdentityService = mock(UserIdentityService.class);
    private final AccountCandidateService accountCandidateService = mock(AccountCandidateService.class);
    private final UserAccessTokenService userAccessTokenService = mock(UserAccessTokenService.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private ThirdPartyLoginService service;

    @BeforeEach
    void setUp() {
        service = new ThirdPartyLoginService(providerRegistry, userIdentityService,
            accountCandidateService, userAccessTokenService, userAccountMapper,
            redisTemplate, new ObjectMapper());
    }

    @Test
    void selectAccountConsumesTicketWithAtomicScript() {
        stubTake(SELECT_TICKET_JSON);
        UserAccountEntity user = activeUser(7L);
        when(userAccountMapper.selectById(7L)).thenReturn(user);
        LoginResponse login = new LoginResponse(7L, 1L, "teacher", "张老师",
            List.of("TEACHER"), List.of(), "Bearer", "token", false, "admin");
        when(userAccessTokenService.issue(user)).thenReturn(login);

        ThirdPartyLoginService.LoginResult result = service.selectAccount("ticket-1", "0", true);

        assertSame(login, result.login());
        assertEquals("wechat_miniapp", result.providerCode());
        verify(accountCandidateService).assertWebPortal(7L);
        verify(userIdentityService).touch(any(ExternalIdentity.class));
        // 旧实现走 opsForValue().getAndDelete()，在 6.2 以下 Redis 上会 500
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void takeScriptIsGetPlusDelAndNeverGetdel() {
        stubTake(SELECT_TICKET_JSON);
        when(userAccountMapper.selectById(7L)).thenReturn(activeUser(7L));
        when(userAccessTokenService.issue(any())).thenReturn(new LoginResponse(7L, 1L, "teacher",
            "张老师", List.of(), List.of(), "Bearer", "token", false, "admin"));

        service.selectAccount("ticket-1", "0", true);

        ArgumentCaptor<RedisScript> captor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(captor.capture(), anyList());
        String script = captor.getValue().getScriptAsString().toUpperCase(Locale.ROOT);
        assertTrue(script.contains("'GET'"), "脚本要先取值");
        assertTrue(script.contains("'DEL'"), "脚本要删除票据");
        assertFalse(script.contains("GETDEL"), "不得依赖 6.2 才有的 GETDEL");
    }

    @Test
    void expiredTicketIsRejected() {
        stubTake(null);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.selectAccount("gone", "0", true));

        assertTrue(exception.getMessage().contains("登录票据已过期"));
    }

    @Test
    void redisFailureBecomesBusinessErrorInsteadOfRaw500() {
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
            .thenThrow(new IllegalStateException("ERR unknown command 'GETDEL'"));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.selectAccount("ticket-1", "0", true));

        assertTrue(exception.getMessage().contains("登录状态读取失败"));
    }

    private void stubTake(String json) {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(json);
    }

    private UserAccountEntity activeUser(Long id) {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(id);
        user.setUsername("teacher");
        user.setStatus("active");
        return user;
    }
}
