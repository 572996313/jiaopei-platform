package com.cornerstone.core.auth.social;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 标准 OAuth2 授权码流程实现（Authorization Code）。
 *
 * <p>只做协议本身，不引任何渠道 SDK：LinuxDo 以及未来任何标准 OAuth2/OIDC
 * 渠道都可以只靠 sys_auth_provider 一行配置接入。用户字段名通过
 * extra_config 的 userIdField/nicknameField/avatarField/unionIdField 映射，
 * 默认按 sub → id → openid → username 依次兜底。</p>
 */
@Component
public class OAuth2ThirdPartyAuthProvider implements ThirdPartyAuthProvider {

    public static final String TYPE = "OAUTH2";

    private static final Logger log = LoggerFactory.getLogger(OAuth2ThirdPartyAuthProvider.class);

    private static final String DEFAULT_APP_TYPE = "website";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OAuth2ThirdPartyAuthProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String buildAuthorizeUrl(AuthProviderConfig config, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(config.authorizeUrl())
            .queryParam("response_type", "code")
            .queryParam("client_id", config.clientId())
            .queryParam("redirect_uri", config.redirectUri())
            .queryParam("state", state);
        if (config.scope() != null && !config.scope().isBlank()) {
            builder.queryParam("scope", config.scope());
        }
        return builder.build().encode().toUriString();
    }

    @Override
    public ExternalIdentity exchange(AuthProviderConfig config, String code) {
        Map<String, Object> fieldMapping = parseExtraConfig(config.extraConfig());
        String appType = text(fieldMapping.get("appType"), DEFAULT_APP_TYPE);

        Map<String, Object> token = requestToken(config, code);
        String accessToken = text(token.get("access_token"), null);
        if (accessToken == null) {
            log.warn("OAuth2 渠道 {} 未返回 access_token", config.providerCode());
            throw new BusinessException(ResultCode.BAD_REQUEST, "第三方登录失败，请稍后重试");
        }

        Map<String, Object> userInfo = requestUserInfo(config, accessToken);
        String openId = firstText(userInfo,
            text(fieldMapping.get("userIdField"), null), "sub", "id", "openid", "username");
        if (openId == null) {
            log.warn("OAuth2 渠道 {} 用户信息缺少唯一标识", config.providerCode());
            throw new BusinessException(ResultCode.BAD_REQUEST, "第三方登录失败：未获取到用户标识");
        }
        String unionId = text(userInfo.get(text(fieldMapping.get("unionIdField"), "unionid")), null);
        String nickname = firstText(userInfo, text(fieldMapping.get("nicknameField"), null),
            "name", "nickname", "username");
        String avatarUrl = text(userInfo.get(text(fieldMapping.get("avatarField"), "avatar")), null);
        return new ExternalIdentity(config.providerCode(), appType, openId, unionId, nickname, avatarUrl);
    }

    private Map<String, Object> requestToken(AuthProviderConfig config, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", config.redirectUri());

        RestClient.RequestBodySpec spec = restClient.post()
            .uri(config.tokenUrl())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON);
        if ("basic".equalsIgnoreCase(config.tokenAuthMethod())) {
            spec = spec.headers(headers ->
                headers.setBasicAuth(config.clientId(), config.clientSecret()));
        } else {
            form.add("client_id", config.clientId());
            form.add("client_secret", config.clientSecret());
        }
        try {
            Map<String, Object> body = spec.body(form).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
            return body == null ? Map.of() : body;
        } catch (Exception exception) {
            // 异常消息可能包含请求 URL，但不含密钥；只记渠道与异常类型，避免日志被刷爆
            log.warn("OAuth2 渠道 {} 换取令牌失败：{}", config.providerCode(), exception.getMessage());
            throw new BusinessException(ResultCode.BAD_REQUEST, "第三方登录失败，请稍后重试");
        }
    }

    private Map<String, Object> requestUserInfo(AuthProviderConfig config, String accessToken) {
        try {
            Map<String, Object> body = restClient.get()
                .uri(config.userinfoUrl())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
            return body == null ? Map.of() : body;
        } catch (Exception exception) {
            log.warn("OAuth2 渠道 {} 获取用户信息失败：{}", config.providerCode(), exception.getMessage());
            throw new BusinessException(ResultCode.BAD_REQUEST, "第三方登录失败：获取用户信息失败");
        }
    }

    private Map<String, Object> parseExtraConfig(String extraConfig) {
        if (extraConfig == null || extraConfig.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(extraConfig,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception exception) {
            log.warn("第三方渠道 extra_config 不是合法 JSON：{}", exception.getMessage());
            return Map.of();
        }
    }

    private String firstText(Map<String, Object> source, String preferred, String... fallbacks) {
        if (preferred != null && !preferred.isBlank()) {
            String value = text(source.get(preferred), null);
            if (value != null) {
                return value;
            }
        }
        for (String key : fallbacks) {
            String value = text(source.get(key), null);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
