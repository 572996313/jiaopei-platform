package com.cornerstone.core.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cornerstone.core.auth.domain.ThirdPartyAuthProviderEntity;
import com.cornerstone.core.auth.domain.UserIdentityEntity;
import com.cornerstone.core.auth.dto.AuthProviderAdminView;
import com.cornerstone.core.auth.dto.AuthProviderSaveRequest;
import com.cornerstone.core.auth.mapper.ThirdPartyAuthProviderMapper;
import com.cornerstone.core.auth.mapper.UserIdentityMapper;
import com.cornerstone.core.auth.service.AuthProviderAdminService;
import com.cornerstone.core.auth.social.AuthProviderRegistry;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.security.SensitiveCipher;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 第三方登录渠道配置的管理端实现。
 *
 * <p>三条刻意保留的口径：</p>
 * <ul>
 *   <li>渠道编码建后不可改——它已经出现在回调路径与身份绑定行里，改了等于把存量绑定全变孤儿；</li>
 *   <li>密钥写入即加密、读取只回传"是否已配置"，留空表示保持原值；</li>
 *   <li>还有绑定用户时不许删除，只能停用，否则那些账号的登录入口会直接消失且没有提示。</li>
 * </ul>
 */
@Service
public class AuthProviderAdminServiceImpl implements AuthProviderAdminService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_DISABLED = "disabled";
    private static final Set<String> STATUSES = Set.of(STATUS_ACTIVE, STATUS_DISABLED);
    private static final String OAUTH2 = "OAUTH2";

    private final ThirdPartyAuthProviderMapper providerMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final AuthProviderRegistry providerRegistry;
    private final SensitiveCipher sensitiveCipher;
    private final ObjectMapper objectMapper;

    public AuthProviderAdminServiceImpl(ThirdPartyAuthProviderMapper providerMapper,
                                        UserIdentityMapper userIdentityMapper,
                                        AuthProviderRegistry providerRegistry,
                                        SensitiveCipher sensitiveCipher,
                                        ObjectMapper objectMapper) {
        this.providerMapper = providerMapper;
        this.userIdentityMapper = userIdentityMapper;
        this.providerRegistry = providerRegistry;
        this.sensitiveCipher = sensitiveCipher;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AuthProviderAdminView> listProviders() {
        return providerMapper.selectList(new LambdaQueryWrapper<ThirdPartyAuthProviderEntity>()
                .orderByAsc(ThirdPartyAuthProviderEntity::getSortNo)
                .orderByAsc(ThirdPartyAuthProviderEntity::getId))
            .stream()
            .map(this::toView)
            .toList();
    }

    @Override
    @Transactional
    public AuthProviderAdminView create(AuthProviderSaveRequest request) {
        String providerCode = normalizeCode(request.providerCode());
        if (!StringUtils.hasText(providerCode)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "渠道编码不能为空");
        }
        assertUniqueCode(providerCode);
        String providerType = normalizeType(request.providerType());
        assertTypeImplemented(providerType);
        String status = normalizeStatus(request.status());
        String extraConfig = normalizeExtraConfig(request.extraConfig());

        ThirdPartyAuthProviderEntity entity = new ThirdPartyAuthProviderEntity();
        entity.setProviderCode(providerCode);
        applyEditable(entity, request, providerType, status, extraConfig);
        entity.setClientSecret(encryptSecret(request.clientSecret()));
        if (STATUS_ACTIVE.equals(status)) {
            assertLoginReady(entity);
        }
        try {
            providerMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "渠道编码已存在:" + providerCode);
        }
        return toView(providerMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional
    public AuthProviderAdminView update(Long id, AuthProviderSaveRequest request) {
        ThirdPartyAuthProviderEntity entity = requireProvider(id);
        String providerType = normalizeType(request.providerType());
        if (!providerType.equalsIgnoreCase(entity.getProviderType())) {
            // 类型决定换票协议；换类型等于换一套实现，存量绑定未必还匹配，按"新建一个渠道"处理
            throw new BusinessException(ResultCode.BAD_REQUEST, "渠道类型建后不可修改，请新建一个渠道");
        }
        String status = normalizeStatus(request.status());
        String extraConfig = normalizeExtraConfig(request.extraConfig());

        applyEditable(entity, request, providerType, status, extraConfig);
        // 留空表示保持原值：编辑态前端拿不到密文，空串不能当清空
        if (StringUtils.hasText(request.clientSecret())) {
            entity.setClientSecret(encryptSecret(request.clientSecret()));
        }
        if (STATUS_ACTIVE.equals(status)) {
            assertLoginReady(entity);
        }
        writeAllFields(entity);
        return toView(providerMapper.selectById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ThirdPartyAuthProviderEntity entity = requireProvider(id);
        long bound = userIdentityMapper.selectCount(new LambdaQueryWrapper<UserIdentityEntity>()
            .eq(UserIdentityEntity::getProviderCode, entity.getProviderCode()));
        if (bound > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该渠道还有 " + bound + " 个账号绑定，请先停用而不是删除");
        }
        providerMapper.physicalDeleteById(id);
    }

    private void applyEditable(ThirdPartyAuthProviderEntity entity, AuthProviderSaveRequest request,
                               String providerType, String status, String extraConfig) {
        entity.setProviderName(request.providerName().trim());
        entity.setProviderType(providerType);
        entity.setIcon(trimToNull(request.icon()));
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus(status);
        entity.setClientId(trimToNull(request.clientId()));
        entity.setAuthorizeUrl(trimToNull(request.authorizeUrl()));
        entity.setTokenUrl(trimToNull(request.tokenUrl()));
        entity.setUserinfoUrl(trimToNull(request.userinfoUrl()));
        entity.setScope(trimToNull(request.scope()));
        entity.setTokenAuthMethod(normalizeTokenAuthMethod(request.tokenAuthMethod()));
        entity.setRedirectUri(trimToNull(request.redirectUri()));
        entity.setExtraConfig(extraConfig);
        entity.setRemark(trimToNull(request.remark()));
    }

    /**
     * 显式 set 每个可编辑列（含 null）。MyBatis-Plus 默认策略会忽略 null 值，
     * 用 updateById 的话"把备注/地址清空"在界面上会表现为保存成功但值还在。
     */
    private void writeAllFields(ThirdPartyAuthProviderEntity entity) {
        providerMapper.update(null, new LambdaUpdateWrapper<ThirdPartyAuthProviderEntity>()
            .eq(ThirdPartyAuthProviderEntity::getId, entity.getId())
            .set(ThirdPartyAuthProviderEntity::getProviderName, entity.getProviderName())
            .set(ThirdPartyAuthProviderEntity::getProviderType, entity.getProviderType())
            .set(ThirdPartyAuthProviderEntity::getIcon, entity.getIcon())
            .set(ThirdPartyAuthProviderEntity::getSortNo, entity.getSortNo())
            .set(ThirdPartyAuthProviderEntity::getStatus, entity.getStatus())
            .set(ThirdPartyAuthProviderEntity::getClientId, entity.getClientId())
            .set(ThirdPartyAuthProviderEntity::getClientSecret, entity.getClientSecret())
            .set(ThirdPartyAuthProviderEntity::getAuthorizeUrl, entity.getAuthorizeUrl())
            .set(ThirdPartyAuthProviderEntity::getTokenUrl, entity.getTokenUrl())
            .set(ThirdPartyAuthProviderEntity::getUserinfoUrl, entity.getUserinfoUrl())
            .set(ThirdPartyAuthProviderEntity::getScope, entity.getScope())
            .set(ThirdPartyAuthProviderEntity::getTokenAuthMethod, entity.getTokenAuthMethod())
            .set(ThirdPartyAuthProviderEntity::getRedirectUri, entity.getRedirectUri())
            .set(ThirdPartyAuthProviderEntity::getExtraConfig, entity.getExtraConfig())
            .set(ThirdPartyAuthProviderEntity::getRemark, entity.getRemark()));
    }

    /** 启用标准 OAuth2 渠道前把登录必需项一次说清，避免"启用成功但点了一直报错" */
    private void assertLoginReady(ThirdPartyAuthProviderEntity entity) {
        if (!OAUTH2.equalsIgnoreCase(entity.getProviderType())) {
            return;
        }
        if (!StringUtils.hasText(entity.getClientId())
            || !StringUtils.hasText(entity.getClientSecret())
            || !StringUtils.hasText(entity.getRedirectUri())
            || !StringUtils.hasText(entity.getAuthorizeUrl())
            || !StringUtils.hasText(entity.getTokenUrl())
            || !StringUtils.hasText(entity.getUserinfoUrl())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "启用标准 OAuth2 渠道前需填好 Client ID、Client Secret、回调地址、授权地址、令牌地址与用户信息地址");
        }
    }

    private void assertUniqueCode(String providerCode) {
        Long count = providerMapper.selectCount(new LambdaQueryWrapper<ThirdPartyAuthProviderEntity>()
            .eq(ThirdPartyAuthProviderEntity::getProviderCode, providerCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "渠道编码已存在:" + providerCode);
        }
    }

    private void assertTypeImplemented(String providerType) {
        if (!providerRegistry.implementedTypes().contains(providerType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "渠道类型 " + providerType + " 没有对应的协议实现，不能手工新增");
        }
    }

    private ThirdPartyAuthProviderEntity requireProvider(Long id) {
        ThirdPartyAuthProviderEntity entity = providerMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "渠道配置不存在");
        }
        return entity;
    }

    private String normalizeCode(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeType(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String value) {
        String status = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态只能是 active 或 disabled");
        }
        return status;
    }

    private String normalizeTokenAuthMethod(String value) {
        String method = trimToNull(value);
        if (method == null) {
            return null;
        }
        String normalized = method.toLowerCase(Locale.ROOT);
        if (!"post".equals(normalized) && !"basic".equals(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "令牌认证方式只能是 post 或 basic");
        }
        return normalized;
    }

    /** 扩展配置必须是合法 JSON 对象，否则登录侧解析时会静默回落默认字段映射 */
    private String normalizeExtraConfig(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node == null || !node.isObject()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "扩展配置必须是 JSON 对象");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "扩展配置不是合法的 JSON");
        }
        return trimmed;
    }

    private String encryptSecret(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : sensitiveCipher.encrypt(trimmed);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AuthProviderAdminView toView(ThirdPartyAuthProviderEntity entity) {
        return new AuthProviderAdminView(
            entity.getId(),
            entity.getProviderCode(),
            entity.getProviderName(),
            entity.getProviderType(),
            entity.getIcon(),
            entity.getSortNo(),
            entity.getStatus(),
            entity.getClientId(),
            StringUtils.hasText(entity.getClientSecret()),
            entity.getAuthorizeUrl(),
            entity.getTokenUrl(),
            entity.getUserinfoUrl(),
            entity.getScope(),
            entity.getTokenAuthMethod(),
            entity.getRedirectUri(),
            entity.getExtraConfig(),
            entity.getRemark(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
