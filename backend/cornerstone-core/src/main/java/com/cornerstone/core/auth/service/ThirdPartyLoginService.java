package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.domain.ThirdPartyAuthProviderEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserIdentityEntity;
import com.cornerstone.core.auth.dto.AccountOptionView;
import com.cornerstone.core.auth.dto.AccountSelectionView;
import com.cornerstone.core.auth.dto.AuthProviderView;
import com.cornerstone.core.auth.dto.BindTicketView;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.dto.UserIdentityView;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.security.UserAccessTokenService;
import com.cornerstone.core.auth.social.AuthProviderConfig;
import com.cornerstone.core.auth.social.AuthProviderRegistry;
import com.cornerstone.core.auth.social.ExternalIdentity;
import com.cornerstone.core.auth.social.ThirdPartyAuthProvider;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 网页端第三方登录编排：授权跳转 → 回调换身份 → 换票/选账号 → 签发令牌。
 *
 * <p>浏览器 URL 里只出现一次性票据，访问令牌一律由前端用票据 POST 换取；
 * 票据、state、绑定票都存在 Redis 且一次性消费，避免令牌泄漏在地址栏/历史记录里。</p>
 */
@Service
public class ThirdPartyLoginService {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyLoginService.class);

    private static final String STATE_KEY_PREFIX = "cornerstone:auth:oauth:state:";
    private static final String LOGIN_TICKET_PREFIX = "cornerstone:auth:oauth:login:";
    private static final String SELECT_TICKET_PREFIX = "cornerstone:auth:oauth:select:";
    private static final String BIND_TICKET_PREFIX = "cornerstone:auth:oauth:bind:";

    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final Duration BIND_TICKET_TTL = Duration.ofMinutes(5);
    private static final Duration LOGIN_TICKET_TTL = Duration.ofMinutes(2);
    private static final Duration SELECT_TICKET_TTL = Duration.ofMinutes(5);

    private static final String ACTIVE = "active";

    /** 原子 GET+DEL：兼容 Redis 6.2 以下（没有 GETDEL 命令） */
    private static final RedisScript<String> TAKE_SCRIPT = new DefaultRedisScript<>(
        "local v = redis.call('GET', KEYS[1]) "
            + "if v then redis.call('DEL', KEYS[1]) end "
            + "return v",
        String.class);

    private final AuthProviderRegistry providerRegistry;
    private final UserIdentityService userIdentityService;
    private final AccountCandidateService accountCandidateService;
    private final UserAccessTokenService userAccessTokenService;
    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ThirdPartyLoginService(AuthProviderRegistry providerRegistry,
                                  UserIdentityService userIdentityService,
                                  AccountCandidateService accountCandidateService,
                                  UserAccessTokenService userAccessTokenService,
                                  UserAccountMapper userAccountMapper,
                                  StringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.userIdentityService = userIdentityService;
        this.accountCandidateService = accountCandidateService;
        this.userAccessTokenService = userAccessTokenService;
        this.userAccountMapper = userAccountMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 登录页可见渠道 */
    public List<AuthProviderView> listLoginProviders() {
        return providerRegistry.listLoginProviders();
    }

    /**
     * 生成授权跳转地址。绑定场景由已登录前端先申请绑定票，这里消费后把 userId 放进 state。
     *
     * @param bindTicket 绑定票；为空表示登录场景
     */
    public String buildAuthorizeUrl(String providerCode, String bindTicket) {
        ThirdPartyAuthProviderEntity entity = providerRegistry.requireEnabled(providerCode);
        AuthProviderConfig config = providerRegistry.toConfig(entity);
        requireConfigured(entity, config);

        Long bindUserId = null;
        if (StringUtils.hasText(bindTicket)) {
            BindTicketPayload payload = take(BIND_TICKET_PREFIX + bindTicket, BindTicketPayload.class);
            if (payload == null || !providerCode.equals(payload.providerCode())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "绑定请求已过期，请重新发起");
            }
            bindUserId = payload.userId();
        }

        String state = UUID.randomUUID().toString().replace("-", "");
        put(STATE_KEY_PREFIX + state, new StatePayload(providerCode, bindUserId), STATE_TTL);
        ThirdPartyAuthProvider provider = providerRegistry.requireProvider(entity.getProviderType());
        return provider.buildAuthorizeUrl(config, state);
    }

    /**
     * 渠道回调：校验 state → 换取外部身份 → 绑定或放行登录，最终 302 回前端页面。
     * 失败也走前端页面，不用 JSON 错误页打断浏览器流程。
     */
    public String handleCallback(String providerCode, String code, String state, String error,
                                 HttpServletRequest request) {
        ThirdPartyAuthProviderEntity entity;
        try {
            entity = providerRegistry.requireEnabled(providerCode);
        } catch (RuntimeException e) {
            log.warn("第三方登录回调渠道不可用：{}", providerCode);
            return errorRedirect(null, "provider_unavailable", request);
        }
        if (StringUtils.hasText(error)) {
            return errorRedirect(entity, "access_denied", request);
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            return errorRedirect(entity, "invalid_callback", request);
        }
        StatePayload statePayload = take(STATE_KEY_PREFIX + state, StatePayload.class);
        if (statePayload == null || !providerCode.equals(statePayload.providerCode())) {
            return errorRedirect(entity, "state_expired", request);
        }

        try {
            AuthProviderConfig config = providerRegistry.toConfig(entity);
            ThirdPartyAuthProvider provider = providerRegistry.requireProvider(entity.getProviderType());
            ExternalIdentity identity = provider.exchange(config, code);
            if (identity == null || !StringUtils.hasText(identity.openId())) {
                return errorRedirect(entity, "identity_missing", request);
            }

            String webBase = resolveWebBase(entity, request);
            if (statePayload.userId() != null) {
                bindIdentity(identity, statePayload.userId());
                return webBase + "/login?identityBound=" + urlEncode(providerCode);
            }
            return loginRedirect(identity, webBase, entity);
        } catch (BusinessException e) {
            log.warn("第三方登录失败（{}）：{}", providerCode, e.getMessage());
            return errorRedirect(entity, "exchange_failed", request);
        } catch (Exception e) {
            log.error("第三方登录回调异常（{}）", providerCode, e);
            return errorRedirect(entity, "exchange_failed", request);
        }
    }

    /** 用一次性登录票换取访问令牌（单账号直接登录场景） */
    @Transactional
    public LoginResult exchange(String ticket) {
        LoginTicketPayload payload = take(LOGIN_TICKET_PREFIX + ticket, LoginTicketPayload.class);
        if (payload == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "登录票据已过期，请重新登录");
        }
        UserAccountEntity user = requireActiveWebUser(payload.userId());
        userIdentityService.touch(payload.identity());
        return new LoginResult(userAccessTokenService.issue(user), payload.identity().providerCode());
    }

    /** 多账号场景：读取票据对应的候选账号（票据不消费，允许刷新页面） */
    public AccountSelectionView selectOptions(String ticket) {
        SelectTicketPayload payload = get(SELECT_TICKET_PREFIX + ticket, SelectTicketPayload.class);
        if (payload == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "登录票据已过期，请重新登录");
        }
        List<AccountOptionView> options = accountCandidateService.build(payload.userIds());
        return new AccountSelectionView(payload.identity().providerCode(), options);
    }

    /** 多账号场景：选定账号后签发票据 */
    @Transactional
    public LoginResult select(String ticket, String accountKey) {
        return selectAccount(ticket, accountKey, true);
    }

    /**
     * 登记一次性选账号票据。网页端与小程序共用同一份 Redis 结构，
     * 业务模块（如小程序微信登录）不用再自己拼票据格式。
     */
    public String createSelectTicket(ExternalIdentity identity, List<Long> userIds) {
        return newTicket(SELECT_TICKET_PREFIX, SELECT_TICKET_TTL,
            new SelectTicketPayload(identity, userIds));
    }

    /**
     * 消费选账号票据并签发令牌。
     *
     * @param requireWebPortal 网页端只放行管理后台账号；小程序允许学生/家长/员工混合选择
     */
    @Transactional
    public LoginResult selectAccount(String ticket, String accountKey, boolean requireWebPortal) {
        SelectTicketPayload payload = take(SELECT_TICKET_PREFIX + ticket, SelectTicketPayload.class);
        if (payload == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "登录票据已过期，请重新登录");
        }
        int index;
        try {
            index = Integer.parseInt(accountKey);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要登录的账号");
        }
        if (index < 0 || index >= payload.userIds().size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要登录的账号");
        }
        UserAccountEntity user = requireActiveUser(payload.userIds().get(index), requireWebPortal);
        userIdentityService.touch(payload.identity());
        return new LoginResult(userAccessTokenService.issue(user), payload.identity().providerCode());
    }

    /** 已登录用户申请绑定票，前端拿票跳到授权地址 */
    public BindTicketView createBindTicket(String providerCode) {
        providerRegistry.requireEnabled(providerCode);
        Long userId = AuthContext.requireUser().userId();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        put(BIND_TICKET_PREFIX + ticket, new BindTicketPayload(providerCode, userId), BIND_TICKET_TTL);
        return new BindTicketView(ticket);
    }

    public List<UserIdentityView> listIdentities() {
        Long userId = AuthContext.requireUser().userId();
        Map<String, String> providerNames = providerRegistry.providerNames();
        List<UserIdentityView> views = new ArrayList<>();
        for (UserIdentityEntity entity : userIdentityService.findByUser(userId)) {
            views.add(new UserIdentityView(
                entity.getId(),
                entity.getProviderCode(),
                providerNames.getOrDefault(entity.getProviderCode(), entity.getProviderCode()),
                entity.getAppType(),
                entity.getNickname(),
                entity.getCreatedAt(),
                entity.getLastLoginAt()));
        }
        return views;
    }

    /**
     * 解绑。账号本身没有密码、且这是最后一个登录方式时禁止解绑，
     * 否则会把自己锁在系统外面。
     */
    public void unbind(Long identityId) {
        Long userId = AuthContext.requireUser().userId();
        List<UserIdentityEntity> identities = userIdentityService.findByUser(userId);
        boolean owned = identities.stream().anyMatch(identity -> identity.getId().equals(identityId));
        if (!owned) {
            throw new BusinessException(ResultCode.NOT_FOUND, "绑定关系不存在");
        }
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user != null && user.getPasswordHash() == null && identities.size() <= 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该账号未设置密码，解绑后无法登录");
        }
        userIdentityService.unbind(userId, identityId);
    }

    private String loginRedirect(ExternalIdentity identity, String webBase,
                                 ThirdPartyAuthProviderEntity entity) {
        List<UserIdentityEntity> identities = userIdentityService.findByExternal(
            identity.providerCode(), identity.appType(), identity.openId(), identity.unionId());
        if (identities.isEmpty()) {
            return errorRedirect(entity, "not_bound", webBase);
        }
        List<Long> userIds = new ArrayList<>(new LinkedHashSet<>(
            identities.stream().map(UserIdentityEntity::getUserId).toList()));
        List<Long> activeUserIds = accountCandidateService.activeUserIds(userIds);
        if (activeUserIds.isEmpty()) {
            return errorRedirect(entity, "account_disabled", webBase);
        }

        List<AccountOptionView> options = accountCandidateService.build(activeUserIds);
        List<AccountOptionView> webOptions = options.stream()
            .filter(option -> PortalResolver.ADMIN.equals(option.portal()))
            .toList();
        if (webOptions.isEmpty()) {
            // 家长/学生端只在小程序里,网页端不签发这类账号的令牌
            return errorRedirect(entity, "portal_not_allowed", webBase);
        }
        if (webOptions.size() == 1) {
            Long userId = activeUserIds.get(Integer.parseInt(webOptions.get(0).key()));
            String ticket = newTicket(LOGIN_TICKET_PREFIX, LOGIN_TICKET_TTL,
                new LoginTicketPayload(identity, userId));
            return webBase + "/oauth/callback#ticket=" + urlEncode(ticket);
        }
        List<Long> selectableUserIds = webOptions.stream()
            .map(option -> activeUserIds.get(Integer.parseInt(option.key())))
            .toList();
        String ticket = newTicket(SELECT_TICKET_PREFIX, SELECT_TICKET_TTL,
            new SelectTicketPayload(identity, selectableUserIds));
        return webBase + "/oauth/select#ticket=" + urlEncode(ticket);
    }

    private void bindIdentity(ExternalIdentity identity, Long userId) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null || !ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号不存在或已被禁用");
        }
        userIdentityService.bind(identity, userId);
        userIdentityService.touch(identity);
    }

    private UserAccountEntity requireActiveWebUser(Long userId) {
        return requireActiveUser(userId, true);
    }

    private UserAccountEntity requireActiveUser(Long userId, boolean requireWebPortal) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null || !ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号不存在或已被禁用");
        }
        if (requireWebPortal) {
            accountCandidateService.assertWebPortal(userId);
        }
        return user;
    }

    private void requireConfigured(ThirdPartyAuthProviderEntity entity, AuthProviderConfig config) {
        if (!StringUtils.hasText(config.clientId()) || !StringUtils.hasText(config.redirectUri())
            || !StringUtils.hasText(config.authorizeUrl()) || !StringUtils.hasText(config.tokenUrl())) {
            log.warn("第三方渠道 {} 配置不完整，无法发起授权", entity.getProviderCode());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "第三方登录渠道配置不完整");
        }
    }

    /**
     * 前端页面基地址：优先 extra_config.webBaseUrl（开发环境前端与后端不同源时必须配），
     * 否则取 redirect_uri 的源（生产同源部署的常规情况）。
     */
    private String resolveWebBase(ThirdPartyAuthProviderEntity entity, HttpServletRequest request) {
        String configured = extraValue(entity.getExtraConfig(), "webBaseUrl");
        if (StringUtils.hasText(configured)) {
            return trimTrailingSlash(configured);
        }
        if (StringUtils.hasText(entity.getRedirectUri())) {
            try {
                URI uri = URI.create(entity.getRedirectUri());
                if (uri.getScheme() != null && uri.getRawAuthority() != null) {
                    return uri.getScheme() + "://" + uri.getRawAuthority();
                }
            } catch (IllegalArgumentException ignored) {
                // 落到请求 Host 兜底
            }
        }
        return requestOrigin(request);
    }

    private String errorRedirect(ThirdPartyAuthProviderEntity entity, String code,
                                 HttpServletRequest request) {
        return errorRedirect(entity, code, resolveWebBaseSafely(entity, request));
    }

    private String errorRedirect(ThirdPartyAuthProviderEntity entity, String code, String webBase) {
        return webBase + "/login?oauthError=" + urlEncode(code);
    }

    private String resolveWebBaseSafely(ThirdPartyAuthProviderEntity entity, HttpServletRequest request) {
        try {
            return entity == null ? requestOrigin(request) : resolveWebBase(entity, request);
        } catch (RuntimeException e) {
            return requestOrigin(request);
        }
    }

    private String requestOrigin(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (!StringUtils.hasText(host)) {
            host = request.getServerName()
                + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "");
        }
        return request.getScheme() + "://" + host;
    }

    private String extraValue(String extraConfig, String key) {
        if (!StringUtils.hasText(extraConfig)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(extraConfig).get(key);
            return node == null || node.isNull() ? null : node.asText();
        } catch (Exception e) {
            log.warn("第三方渠道 extra_config 解析失败：{}", e.getMessage());
            return null;
        }
    }

    private String newTicket(String prefix, Duration ttl, Object payload) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        put(prefix + ticket, payload, ttl);
        return ticket;
    }

    private void put(String key, Object payload, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), ttl);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "登录状态保存失败，请稍后重试");
        }
    }

    private <T> T get(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(key);
        return json == null ? null : read(json, type);
    }

    /**
     * 一次性消费：取出即删除，重放直接失效。
     *
     * <p>不用 {@code opsForValue().getAndDelete()}：它发的是 {@code GETDEL}，Redis 6.2 才有，
     * 本地与部分现场跑的是更老的版本，会直接报 {@code ERR unknown command 'GETDEL'}。
     * EVAL 从 2.6 起就有，脚本里 GET+DEL 仍然保证"取到即删"的原子性。</p>
     */
    private <T> T take(String key, Class<T> type) {
        String json;
        try {
            json = redisTemplate.execute(TAKE_SCRIPT, List.of(key));
        } catch (Exception e) {
            log.warn("第三方登录票据读取失败：{}", e.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "登录状态读取失败，请稍后重试");
        }
        return json == null ? null : read(json, type);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("第三方登录票据解析失败：{}", e.getMessage());
            return null;
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** 登录结果附带渠道编码，控制器写入请求属性供操作留痕记录 */
    public record LoginResult(LoginResponse login, String providerCode) {
    }

    record StatePayload(String providerCode, Long userId) {
    }

    record LoginTicketPayload(ExternalIdentity identity, Long userId) {
    }

    record SelectTicketPayload(ExternalIdentity identity, List<Long> userIds) {
    }

    record BindTicketPayload(String providerCode, Long userId) {
    }
}
