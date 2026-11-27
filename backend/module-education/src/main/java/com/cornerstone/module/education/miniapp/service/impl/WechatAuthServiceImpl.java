package com.cornerstone.module.education.miniapp.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.config.WxMaConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
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
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.module.education.miniapp.dto.WechatLoginRequest;
import com.cornerstone.module.education.miniapp.dto.WechatLoginResponse;
import com.cornerstone.module.education.miniapp.dto.WechatRegisterRequest;
import com.cornerstone.module.education.miniapp.service.WechatAuthService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class WechatAuthServiceImpl implements WechatAuthService {

    private static final String ACTIVE = "active";
    private static final String DEFAULT_DISPLAY_NAME = "微信用户";
    private static final String DEFAULT_INSTITUTION_CONFIG_KEY = "auth.register.default_institution_id";

    /** 微信小程序在通用身份表里的渠道键：一个微信可同时绑定家长与员工账号 */
    private static final String PROVIDER_CODE = "wechat_miniapp";
    private static final String APP_TYPE = "miniapp";

    // 微信真实登录凭证为字母数字组合(如 081Kk0ll2xxx);含空格等字符必然是无效值
    private static final Pattern VALID_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    /** 邀请码扫码注册放行的角色白名单:仅学生/家长,防止把管理类邀请码贴进小程序提权 */
    private static final Set<String> INVITE_ALLOWED_ROLES =
        Set.of(MiniappPortalResolver.ROLE_CODE_STUDENT, MiniappPortalResolver.ROLE_CODE_PARENT);

    private final WxMaService wxMaService;
    private final UserIdentityService userIdentityService;
    private final AccountCandidateService accountCandidateService;
    private final ThirdPartyLoginService thirdPartyLoginService;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final UserAccessTokenService userAccessTokenService;
    private final TokenCacheService tokenCacheService;
    private final SystemConfigService systemConfigService;
    private final AuthInvitationService invitationService;
    private final ApplicationEventPublisher eventPublisher;

    public WechatAuthServiceImpl(WxMaService wxMaService,
                                 UserIdentityService userIdentityService,
                                 AccountCandidateService accountCandidateService,
                                 ThirdPartyLoginService thirdPartyLoginService,
                                 UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                 UserRoleRelMapper userRoleRelMapper,
                                 UserAccessTokenService userAccessTokenService,
                                 TokenCacheService tokenCacheService, SystemConfigService systemConfigService,
                                 AuthInvitationService invitationService,
                                 ApplicationEventPublisher eventPublisher) {
        this.wxMaService = wxMaService;
        this.userIdentityService = userIdentityService;
        this.accountCandidateService = accountCandidateService;
        this.thirdPartyLoginService = thirdPartyLoginService;
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.userAccessTokenService = userAccessTokenService;
        this.tokenCacheService = tokenCacheService;
        this.systemConfigService = systemConfigService;
        this.invitationService = invitationService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public WechatLoginResponse login(WechatLoginRequest request) {
        WxMaJscode2SessionResult session = code2Session(request.code());
        ExternalIdentity identity = identity(session, null);
        List<UserIdentityEntity> identities = findByExternal(identity);
        if (identities.isEmpty()) {
            // 未注册不视为错误,由前端引导进入身份选择注册流程
            return new WechatLoginResponse(Boolean.FALSE, null, null, List.of());
        }

        List<Long> userIds = distinctUserIds(identities);
        List<Long> activeUserIds = accountCandidateService.activeUserIds(userIds);
        if (activeUserIds.isEmpty()) {
            throw new DisabledException("账号已被禁用");
        }
        if (activeUserIds.size() == 1) {
            UserAccountEntity user = requireActiveUser(activeUserIds.get(0));
            userIdentityService.touch(identity);
            return new WechatLoginResponse(Boolean.TRUE, userAccessTokenService.issue(user), null, List.of());
        }

        // 同一个微信命中多个账号(家长 + 员工):返回候选列表与一次性票据,由前端选账号
        List<AccountOptionView> accounts = accountCandidateService.build(activeUserIds);
        String ticket = thirdPartyLoginService.createSelectTicket(identity, activeUserIds);
        return new WechatLoginResponse(Boolean.TRUE, null, ticket, accounts);
    }

    @Override
    @Transactional
    public LoginResponse select(String ticket, String accountKey) {
        // 小程序允许学生/家长/员工混合选择,不套网页端只放行管理后台的限制
        return thirdPartyLoginService.selectAccount(ticket, accountKey, false).login();
    }

    @Override
    @Transactional
    public LoginResponse register(WechatRegisterRequest request) {
        WxMaJscode2SessionResult session = code2Session(request.code());
        ExternalIdentity identity = identity(session, request.nickname());
        List<UserIdentityEntity> identities = findByExternal(identity);
        if (!identities.isEmpty()) {
            // 幂等:同一微信重复提交注册时按已注册用户直接登录,邀请码不核销(该人已有账号)
            List<Long> activeUserIds = accountCandidateService.activeUserIds(distinctUserIds(identities));
            if (activeUserIds.isEmpty()) {
                throw new DisabledException("账号已被禁用");
            }
            userIdentityService.touch(identity);
            return userAccessTokenService.issue(requireActiveUser(activeUserIds.get(0)));
        }

        UserAccountEntity user = hasText(request.invitationCode())
            ? createInvitedUser(session, request)
            : createSelfRegisteredUser(session, request);
        userIdentityService.bind(identity, user.getId());
        userIdentityService.touch(identity);
        return userAccessTokenService.issue(user);
    }

    /** 已登录账号在当前微信里补登记一条绑定（员工账号在“我的”页绑定微信） */
    @Override
    @Transactional
    public void bind(WechatLoginRequest request) {
        Long userId = AuthContext.requireUser().userId();
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在");
        }
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }
        WxMaJscode2SessionResult session = code2Session(request.code());
        ExternalIdentity identity = identity(session, user.getDisplayName());
        userIdentityService.bind(identity, userId);
        userIdentityService.touch(identity);
    }

    @Override
    @Transactional
    public void deregister() {
        // 仅允许小程序自助注册的账号自助注销;后台创建的管理账号不得走此通道
        Long userId = AuthContext.requireUser().userId();
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在");
        }
        if (!MiniappPortalResolver.REGISTER_SOURCE_MINIAPP.equals(user.getRegisterSource())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该账号不支持自助注销");
        }
        // 先清令牌,再物理删除账号数据,注销后同一微信可重新注册为全新账号
        tokenCacheService.invalidateUserTokens(userId);
        userRoleRelMapper.physicalDeleteByUserId(userId);
        // 学员/教师档案归属、用户-学员关联、第三方身份绑定的清理都在 EducationDeregistrationListener,
        // 与管理端代注销共用同一条路径（同事务）
        eventPublisher.publishEvent(new UserAccountDeregisteredEvent(userId));
        userAccountMapper.physicalDeleteById(userId);
    }

    /** 邀请码扫码注册:机构与角色由邀请码固定,账号建成无密码态,仅放行学生/家长角色。 */
    private UserAccountEntity createInvitedUser(WxMaJscode2SessionResult session, WechatRegisterRequest request) {
        InvitationPrecheckView precheck = invitationService.precheck(request.invitationCode().trim());
        if (!Boolean.TRUE.equals(precheck.valid())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                precheck.message() == null ? "邀请码无效" : precheck.message());
        }
        if (!INVITE_ALLOWED_ROLES.contains(precheck.roleCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该邀请码角色不支持小程序自助注册");
        }
        // openid 全局唯一,作为账号名天然防重,与无邀请码自助注册同一命名口径
        return invitationService.registerInvitedAccount(new InvitationAccountSpec(
            request.invitationCode().trim(),
            "wx_" + session.getOpenid(),
            resolveDisplayName(request.nickname()),
            null,
            MiniappPortalResolver.REGISTER_SOURCE_MINIAPP));
    }

    /** 无邀请码的自助注册:机构取系统默认,角色按所选身份分配。 */
    private UserAccountEntity createSelfRegisteredUser(WxMaJscode2SessionResult session,
                                                       WechatRegisterRequest request) {
        String role = request.role();
        if (role == null || (!"STUDENT".equals(role) && !"PARENT".equals(role))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "注册身份仅支持 STUDENT 或 PARENT");
        }
        UserAccountEntity user = createMiniappUser(session, request);
        attachRole(user.getId(), resolveRoleCode(role));
        return user;
    }

    private UserAccountEntity createMiniappUser(WxMaJscode2SessionResult session, WechatRegisterRequest request) {
        UserAccountEntity user = new UserAccountEntity();
        user.setInstitutionId(Long.valueOf(
            systemConfigService.getIntConfig(DEFAULT_INSTITUTION_CONFIG_KEY, 1)));
        // openid 全局唯一,作为账号名天然防重
        user.setUsername("wx_" + session.getOpenid());
        user.setDisplayName(resolveDisplayName(request.nickname()));
        user.setStatus(ACTIVE);
        user.setRegisterSource(MiniappPortalResolver.REGISTER_SOURCE_MINIAPP);
        // 无密码账号:不设任何改密标志,账号密码登录在入口处直接拒绝
        user.setPasswordResetRequired(Boolean.FALSE);
        user.setForceChangePassword(Boolean.FALSE);
        userAccountMapper.insert(user);
        return user;
    }

    private String resolveRoleCode(String role) {
        return "STUDENT".equals(role)
            ? MiniappPortalResolver.ROLE_CODE_STUDENT
            : MiniappPortalResolver.ROLE_CODE_PARENT;
    }

    private void attachRole(Long userId, String roleCode) {
        RoleEntity role = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
            .eq(RoleEntity::getRoleCode, roleCode));
        if (role == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "小程序角色未初始化:" + roleCode);
        }
        UserRoleRelEntity relation = new UserRoleRelEntity();
        relation.setUserId(userId);
        relation.setRoleId(role.getId());
        userRoleRelMapper.insert(relation);
    }

    private String resolveDisplayName(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        return nickname.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ExternalIdentity identity(WxMaJscode2SessionResult session, String nickname) {
        // code2Session 不返回昵称/头像;注册时用户填的昵称作为快照写入
        return new ExternalIdentity(PROVIDER_CODE, APP_TYPE, session.getOpenid(), session.getUnionid(),
            hasText(nickname) ? nickname.trim() : null, null);
    }

    private List<UserIdentityEntity> findByExternal(ExternalIdentity identity) {
        return userIdentityService.findByExternal(identity.providerCode(), identity.appType(),
            identity.openId(), identity.unionId());
    }

    private List<Long> distinctUserIds(List<UserIdentityEntity> identities) {
        return new ArrayList<>(new LinkedHashSet<>(
            identities.stream().map(UserIdentityEntity::getUserId).toList()));
    }

    private WxMaJscode2SessionResult code2Session(String jsCode) {
        WxMaConfig config = wxMaService.getWxMaConfig();
        if (config == null || config.getAppid() == null || config.getAppid().isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "微信小程序未配置,请在后端设置 wx.miniapp.appid/secret");
        }
        if (jsCode == null || !VALID_CODE_PATTERN.matcher(jsCode.trim()).matches()) {
            // 开发者工具游客模式/未配置AppID时 wx.login 会返回模拟 code("the code is a mock one"),
            // 其中含空格,拼接请求 URL 时会直接抛 URI 异常;这里提前拦截并给出可操作的提示
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "登录凭证无效:微信开发者工具未返回真实 code,请用微信账号(小程序项目成员)登录工具后重试");
        }
        try {
            return wxMaService.getUserService().getSessionInfo(jsCode.trim());
        } catch (WxErrorException exception) {
            // 微信返回了明确错误(code 无效/appid 或 secret 不对等),消息透出便于对照 errcode 排查
            throw new BusinessException(ResultCode.BAD_REQUEST, "微信登录失败:" + exception.getMessage());
        } catch (Exception exception) {
            // 网络不通、代理拦截、响应解析失败等非业务异常:记下堆栈并把根因带回给调用方,避免只剩笼统的 500
            log.error("微信 jscode2session 调用异常", exception);
            throw new BusinessException(ResultCode.BAD_REQUEST, "微信服务调用异常:" + exception.getMessage());
        }
    }

    private UserAccountEntity requireActiveUser(Long userId) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "绑定的系统用户已不存在,请重新注册");
        }
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }
        return user;
    }
}
