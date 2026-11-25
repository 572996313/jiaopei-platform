package com.cornerstone.core.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.auth.domain.ThirdPartyAuthProviderEntity;
import com.cornerstone.core.auth.dto.AuthProviderAdminView;
import com.cornerstone.core.auth.dto.AuthProviderSaveRequest;
import com.cornerstone.core.auth.mapper.ThirdPartyAuthProviderMapper;
import com.cornerstone.core.auth.mapper.UserIdentityMapper;
import com.cornerstone.core.auth.service.impl.AuthProviderAdminServiceImpl;
import com.cornerstone.core.auth.social.AuthProviderRegistry;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.security.SensitiveCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthProviderAdminServiceImplTest {

    private ThirdPartyAuthProviderMapper providerMapper;
    private UserIdentityMapper userIdentityMapper;
    private AuthProviderRegistry providerRegistry;
    private SensitiveCipher sensitiveCipher;
    private AuthProviderAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        // LambdaUpdateWrapper 依赖 MyBatis-Plus 的实体元数据缓存；纯单测没有 Mapper 扫描，需手建一次
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            ThirdPartyAuthProviderEntity.class);
        providerMapper = mock(ThirdPartyAuthProviderMapper.class);
        userIdentityMapper = mock(UserIdentityMapper.class);
        providerRegistry = mock(AuthProviderRegistry.class);
        sensitiveCipher = mock(SensitiveCipher.class);
        when(providerRegistry.implementedTypes()).thenReturn(Set.of("OAUTH2"));
        when(sensitiveCipher.encrypt(any())).thenAnswer(inv -> "enc:v1:" + inv.getArgument(0));
        service = new AuthProviderAdminServiceImpl(providerMapper, userIdentityMapper,
            providerRegistry, sensitiveCipher, new ObjectMapper());
    }

    @Test
    void createEncryptsSecretAndOnlyReportsWhetherItExists() {
        ThirdPartyAuthProviderEntity[] saved = new ThirdPartyAuthProviderEntity[1];
        when(providerMapper.insert(any(ThirdPartyAuthProviderEntity.class))).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            saved[0].setId(9L);
            return 1;
        });
        when(providerMapper.selectById(9L)).thenAnswer(inv -> saved[0]);

        AuthProviderAdminView view = service.create(activeOauth2Request("demo"));

        assertEquals("enc:v1:s3cret", saved[0].getClientSecret());
        assertEquals("demo", saved[0].getProviderCode());
        assertTrue(view.hasClientSecret());
        assertEquals("demo", view.providerCode());
        assertTrue(view.toString().indexOf("s3cret") < 0, "视图不得回传密钥");
    }

    @Test
    void createRejectsTypeWithoutProtocolImplementation() {
        AuthProviderSaveRequest request = minimalRequest("custom", "UNKNOWN", "disabled");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));

        assertTrue(exception.getMessage().contains("没有对应的协议实现"));
        verify(providerMapper, never()).insert(any(ThirdPartyAuthProviderEntity.class));
    }

    @Test
    void updateKeepsExistingSecretWhenBlank() {
        ThirdPartyAuthProviderEntity existing = entity(1L, "linuxdo", "disabled");
        existing.setClientSecret("enc:v1:old");
        when(providerMapper.selectById(1L)).thenReturn(existing);

        AuthProviderSaveRequest request = minimalRequest("linuxdo", "OAUTH2", "disabled");

        service.update(1L, request);

        assertEquals("enc:v1:old", existing.getClientSecret());
        verify(sensitiveCipher, never()).encrypt(any());
    }

    @Test
    void updateToActiveRequiresOauth2Endpoints() {
        ThirdPartyAuthProviderEntity existing = entity(1L, "linuxdo", "disabled");
        when(providerMapper.selectById(1L)).thenReturn(existing);

        AuthProviderSaveRequest request = new AuthProviderSaveRequest(
            "linuxdo", "LinuxDo", "OAUTH2", null, 10, "active",
            "client-id", null, null, null, null, null, null, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(1L, request));

        assertTrue(exception.getMessage().contains("启用标准 OAuth2 渠道前"));
    }

    @Test
    void updateRejectsTypeChange() {
        ThirdPartyAuthProviderEntity existing = entity(1L, "linuxdo", "disabled");
        when(providerMapper.selectById(1L)).thenReturn(existing);

        AuthProviderSaveRequest request = minimalRequest("linuxdo", "WECHAT_MINIAPP", "disabled");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(1L, request));

        assertTrue(exception.getMessage().contains("渠道类型建后不可修改"));
    }

    @Test
    void deleteIsRejectedWhileAccountsAreStillBound() {
        when(providerMapper.selectById(1L)).thenReturn(entity(1L, "linuxdo", "disabled"));
        when(userIdentityMapper.selectCount(any())).thenReturn(2L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertTrue(exception.getMessage().contains("还有 2 个账号绑定"));
        verify(providerMapper, never()).physicalDeleteById(anyLong());
    }

    @Test
    void deleteRemovesProviderWithoutBindings() {
        when(providerMapper.selectById(1L)).thenReturn(entity(1L, "linuxdo", "disabled"));
        when(userIdentityMapper.selectCount(any())).thenReturn(0L);

        service.delete(1L);

        verify(providerMapper).physicalDeleteById(1L);
    }

    @Test
    void createRejectsNonObjectExtraConfig() {
        AuthProviderSaveRequest request = new AuthProviderSaveRequest(
            "demo", "演示渠道", "OAUTH2", null, 0, "disabled",
            null, null, null, null, null, null, null, null, "[1,2]", null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));

        assertTrue(exception.getMessage().contains("必须是 JSON 对象"));
    }

    private AuthProviderSaveRequest activeOauth2Request(String code) {
        return new AuthProviderSaveRequest(
            code, "演示渠道", "OAUTH2", "demo", 10, "active",
            "client-id", "s3cret",
            "https://example.com/oauth2/authorize", "https://example.com/oauth2/token",
            "https://example.com/api/user", "user", "post",
            "https://example.com/api/auth/oauth2/" + code + "/callback", null, "备注");
    }

    private AuthProviderSaveRequest minimalRequest(String code, String type, String status) {
        return new AuthProviderSaveRequest(code, "演示渠道", type, null, 10, status,
            null, null, null, null, null, null, null, null, null, null);
    }

    private ThirdPartyAuthProviderEntity entity(Long id, String code, String status) {
        ThirdPartyAuthProviderEntity entity = new ThirdPartyAuthProviderEntity();
        entity.setId(id);
        entity.setProviderCode(code);
        entity.setProviderName("LinuxDo");
        entity.setProviderType("OAUTH2");
        entity.setStatus(status);
        entity.setSortNo(10);
        return entity;
    }
}
