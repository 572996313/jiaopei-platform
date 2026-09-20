package com.cornerstone.core.auth.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.config.WxMaConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.domain.WechatBindingEntity;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.dto.WechatLoginRequest;
import com.cornerstone.core.auth.dto.WechatLoginResponse;
import com.cornerstone.core.auth.dto.WechatRegisterRequest;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.event.UserAccountDeregisteredEvent;
import com.cornerstone.core.auth.mapper.WechatBindingMapper;
import com.cornerstone.core.auth.security.UserAccessTokenService;
import com.cornerstone.core.auth.service.TokenCacheService;
import com.cornerstone.core.auth.service.WechatAuthService;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.SystemConfigService;
import java.time.LocalDateTime;
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
    private static final String REGISTER_SOURCE_MINIAPP = "WECHAT_MINIAPP";
    private static final String DEFAULT_DISPLAY_NAME = "微信用户";
    private static final String ROLE_CODE_MINIAPP_STUDENT = "MINIAPP_STUDENT";
    private static final String ROLE_CODE_MINIAPP_PARENT = "MINIAPP_PARENT";
    private static final String DEFAULT_INSTITUTION_CONFIG_KEY = "auth.register.default_institution_id";

    // 微信真实登录凭证为字母数字组合(如 081Kk0ll2xxx);含空格等字符必然是无效值
    private static final Pattern VALID_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    private final WxMaService wxMaService;
    private final WechatBindingMapper wechatBindingMapper;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final UserAccessTokenService userAccessTokenService;
    private final TokenCacheService tokenCacheService;
    private final SystemConfigService systemConfigService;
    private final ApplicationEventPublisher eventPublisher;

    public WechatAuthServiceImpl(WxMaService wxMaService, WechatBindingMapper wechatBindingMapper,
                                 UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                 UserRoleRelMapper userRoleRelMapper,
                                 UserAccessTokenService userAccessTokenService,
                                 TokenCacheService tokenCacheService, SystemConfigService systemConfigService,
                                 ApplicationEventPublisher eventPublisher) {
        this.wxMaService = wxMaService;
        this.wechatBindingMapper = wechatBindingMapper;
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.userAccessTokenService = userAccessTokenService;
        this.tokenCacheService = tokenCacheService;
        this.systemConfigService = systemConfigService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public WechatLoginResponse login(WechatLoginRequest request) {
        WxMaJscode2SessionResult session = code2Session(request.code());
        WechatBindingEntity binding = findBinding(session.getOpenid());
        if (binding == null) {
            // 未注册不视为错误,由前端引导进入身份选择注册流程
            return new WechatLoginResponse(Boolean.FALSE, null);
        }
        UserAccountEntity user = requireActiveUser(binding.getUserId());
        LoginResponse auth = userAccessTokenService.issue(user);
        binding.setLastLoginAt(LocalDateTime.now());
        wechatBindingMapper.updateById(binding);
        return new WechatLoginResponse(Boolean.TRUE, auth);
    }

    @Override
    @Transactional
    public LoginResponse register(WechatRegisterRequest request) {
        WxMaJscode2SessionResult session = code2Session(request.code());
        WechatBindingEntity binding = findBinding(session.getOpenid());
        if (binding != null) {
            // 幂等:同一微信重复提交注册时按已注册用户直接登录
            UserAccountEntity existing = requireActiveUser(binding.getUserId());
            binding.setLastLoginAt(LocalDateTime.now());
            wechatBindingMapper.updateById(binding);
            return userAccessTokenService.issue(existing);
        }

        UserAccountEntity user = createMiniappUser(session, request);
        attachRole(user.getId(), resolveRoleCode(request.role()));

        WechatBindingEntity created = new WechatBindingEntity();
        created.setOpenid(session.getOpenid());
        created.setUnionId(session.getUnionid());
        created.setUserId(user.getId());
        created.setLastLoginAt(LocalDateTime.now());
        wechatBindingMapper.insert(created);

        return userAccessTokenService.issue(user);
    }

    private UserAccountEntity createMiniappUser(WxMaJscode2SessionResult session, WechatRegisterRequest request) {
        UserAccountEntity user = new UserAccountEntity();
        user.setInstitutionId(Long.valueOf(
            systemConfigService.getIntConfig(DEFAULT_INSTITUTION_CONFIG_KEY, 1)));
        // openid 全局唯一,作为账号名天然防重
        user.setUsername("wx_" + session.getOpenid());
        user.setDisplayName(resolveDisplayName(request.nickname()));
        user.setStatus(ACTIVE);
        user.setRegisterSource(REGISTER_SOURCE_MINIAPP);
        // 无密码账号:不设任何改密标志,账号密码登录在入口处直接拒绝
        user.setPasswordResetRequired(Boolean.FALSE);
        user.setForceChangePassword(Boolean.FALSE);
        userAccountMapper.insert(user);
        return user;
    }

    private String resolveRoleCode(String role) {
        return "STUDENT".equals(role) ? ROLE_CODE_MINIAPP_STUDENT : ROLE_CODE_MINIAPP_PARENT;
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

    private WechatBindingEntity findBinding(String openid) {
        return wechatBindingMapper.selectOne(new LambdaQueryWrapper<WechatBindingEntity>()
            .eq(WechatBindingEntity::getOpenid, openid));
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

    @Override
    @Transactional
    public void deregister() {
        // 仅允许小程序自助注册的账号自助注销;后台创建的管理账号不得走此通道
        Long userId = AuthContext.requireUser().userId();
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在");
        }
        if (!REGISTER_SOURCE_MINIAPP.equals(user.getRegisterSource())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该账号不支持自助注销");
        }
        // 先清令牌,再物理删除账号数据,注销后同一微信可重新注册为全新账号
        tokenCacheService.invalidateUserTokens(userId);
        userRoleRelMapper.physicalDeleteByUserId(userId);
        wechatBindingMapper.physicalDeleteByUserId(userId);
        // 学员档案归属与用户-学员关联的清理由业务模块监听事件完成（同事务），
        // 避免底座直接依赖教务域
        eventPublisher.publishEvent(new UserAccountDeregisteredEvent(userId));
        userAccountMapper.physicalDeleteById(userId);
    }
}
