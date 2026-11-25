package com.cornerstone.core.auth.social;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.domain.ThirdPartyAuthProviderEntity;
import com.cornerstone.core.auth.dto.AuthProviderView;
import com.cornerstone.core.auth.mapper.ThirdPartyAuthProviderMapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.security.SensitiveCipher;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 第三方渠道注册表：把 sys_auth_provider 的配置行与各渠道实现对上。
 *
 * <p>行在库里、协议实现在代码里：标准 OAuth2 渠道只需建一行；微信这类
 * 换票协议不同的渠道，由业务模块提供 {@link ThirdPartyAuthProvider} 实现。
 * 某行对应的实现缺失时只把该行视为不可用，不影响其它渠道与系统启动。</p>
 */
@Component
public class AuthProviderRegistry {

    public static final String STATUS_ACTIVE = "active";

    private final ThirdPartyAuthProviderMapper providerMapper;
    private final SensitiveCipher sensitiveCipher;
    private final Map<String, ThirdPartyAuthProvider> providersByType;

    public AuthProviderRegistry(ThirdPartyAuthProviderMapper providerMapper,
                                SensitiveCipher sensitiveCipher,
                                List<ThirdPartyAuthProvider> providers) {
        this.providerMapper = providerMapper;
        this.sensitiveCipher = sensitiveCipher;
        Map<String, ThirdPartyAuthProvider> map = new LinkedHashMap<>();
        for (ThirdPartyAuthProvider provider : providers) {
            map.put(provider.type(), provider);
        }
        this.providersByType = Map.copyOf(map);
    }

    /** 登录页可用的渠道：启用中、且有对应协议实现 */
    public List<AuthProviderView> listLoginProviders() {
        return listEnabled().stream()
            .filter(entity -> providersByType.containsKey(entity.getProviderType()))
            .sorted(Comparator.comparing(entity -> entity.getSortNo() == null ? 0 : entity.getSortNo()))
            .map(entity -> new AuthProviderView(
                entity.getProviderCode(), entity.getProviderName(), entity.getIcon(), entity.getProviderType()))
            .toList();
    }

    public ThirdPartyAuthProviderEntity requireEnabled(String providerCode) {
        ThirdPartyAuthProviderEntity entity = providerMapper.selectOne(
            new LambdaQueryWrapper<ThirdPartyAuthProviderEntity>()
                .eq(ThirdPartyAuthProviderEntity::getProviderCode, providerCode));
        if (entity == null || !STATUS_ACTIVE.equalsIgnoreCase(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "第三方登录渠道不存在或已停用");
        }
        if (!providersByType.containsKey(entity.getProviderType())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "第三方登录渠道未实现：" + providerCode);
        }
        return entity;
    }

    public AuthProviderConfig requireConfig(String providerCode) {
        return toConfig(requireEnabled(providerCode));
    }

    public ThirdPartyAuthProvider requireProvider(String providerType) {
        ThirdPartyAuthProvider provider = providersByType.get(providerType);
        if (provider == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "第三方登录类型未实现：" + providerType);
        }
        return provider;
    }

    public AuthProviderConfig toConfig(ThirdPartyAuthProviderEntity entity) {
        return new AuthProviderConfig(
            entity.getProviderCode(),
            entity.getProviderType(),
            entity.getClientId(),
            sensitiveCipher.decrypt(entity.getClientSecret()),
            entity.getAuthorizeUrl(),
            entity.getTokenUrl(),
            entity.getUserinfoUrl(),
            entity.getScope(),
            entity.getTokenAuthMethod(),
            entity.getRedirectUri(),
            entity.getExtraConfig()
        );
    }

    /** 全部渠道的编码 → 名称，用于已绑定列表展示（含已停用渠道） */
    public Map<String, String> providerNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (ThirdPartyAuthProviderEntity entity : providerMapper.selectList(null)) {
            names.put(entity.getProviderCode(), entity.getProviderName());
        }
        return names;
    }

    private List<ThirdPartyAuthProviderEntity> listEnabled() {
        return providerMapper.selectList(new LambdaQueryWrapper<ThirdPartyAuthProviderEntity>()
            .eq(ThirdPartyAuthProviderEntity::getStatus, STATUS_ACTIVE));
    }
}
