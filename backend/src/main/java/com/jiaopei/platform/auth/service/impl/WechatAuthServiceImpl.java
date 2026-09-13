package com.jiaopei.platform.auth.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.config.WxMaConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.auth.domain.UserAccountEntity;
import com.jiaopei.platform.auth.domain.WechatBindingEntity;
import com.jiaopei.platform.auth.dto.LoginResponse;
import com.jiaopei.platform.auth.dto.WechatBindRequest;
import com.jiaopei.platform.auth.dto.WechatLoginRequest;
import com.jiaopei.platform.auth.dto.WechatLoginResponse;
import com.jiaopei.platform.auth.mapper.UserAccountMapper;
import com.jiaopei.platform.auth.mapper.WechatBindingMapper;
import com.jiaopei.platform.auth.security.UserAccessTokenService;
import com.jiaopei.platform.auth.service.WechatAuthService;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class WechatAuthServiceImpl implements WechatAuthService {

    private static final String ACTIVE = "active";

    // 微信真实登录凭证为字母数字组合(如 081Kk0ll2xxx);含空格等字符必然是无效值
    private static final Pattern VALID_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    private final WxMaService wxMaService;
    private final WechatBindingMapper wechatBindingMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserAccessTokenService userAccessTokenService;
    private final PasswordEncoder passwordEncoder;

    public WechatAuthServiceImpl(WxMaService wxMaService, WechatBindingMapper wechatBindingMapper,
                                 UserAccountMapper userAccountMapper, UserAccessTokenService userAccessTokenService,
                                 PasswordEncoder passwordEncoder) {
        this.wxMaService = wxMaService;
        this.wechatBindingMapper = wechatBindingMapper;
        this.userAccountMapper = userAccountMapper;
        this.userAccessTokenService = userAccessTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public WechatLoginResponse login(WechatLoginRequest request) {
        WxMaJscode2SessionResult session = code2Session(request.code());
        WechatBindingEntity binding = wechatBindingMapper.selectOne(new LambdaQueryWrapper<WechatBindingEntity>()
            .eq(WechatBindingEntity::getOpenid, session.getOpenid()));
        if (binding == null) {
            // 未绑定不视为错误,由前端引导进入账号密码绑定流程
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
    public LoginResponse bind(WechatBindRequest request) {
        WxMaJscode2SessionResult session = code2Session(request.code());
        UserAccountEntity user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
            .eq(UserAccountEntity::getUsername, request.username().trim()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }
        // openid 唯一,同一微信重复绑定时改绑到新账号
        WechatBindingEntity binding = wechatBindingMapper.selectOne(new LambdaQueryWrapper<WechatBindingEntity>()
            .eq(WechatBindingEntity::getOpenid, session.getOpenid()));
        if (binding == null) {
            binding = new WechatBindingEntity();
            binding.setOpenid(session.getOpenid());
        }
        binding.setUnionId(session.getUnionid());
        binding.setUserId(user.getId());
        binding.setLastLoginAt(LocalDateTime.now());
        if (binding.getId() == null) {
            wechatBindingMapper.insert(binding);
        } else {
            wechatBindingMapper.updateById(binding);
        }
        return userAccessTokenService.issue(user);
    }

    private WxMaJscode2SessionResult code2Session(String jsCode) {
        WxMaConfig config = wxMaService.getWxMaConfig();
        if (config == null || config.getAppid() == null || config.getAppid().isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "微信小程序未配置,请在后端设置 wx.miniapp.appid/secret");
        }
        if (jsCode == null || !VALID_CODE_PATTERN.matcher(jsCode.trim()).matches()) {
            // 开发者工具未登录/用测试号时 wx.login 会返回模拟 code("the code is a mock one"),
            // 其中含空格,拼接请求 URL 时会直接抛 URI 异常;这里提前拦截并给出可操作的提示
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "登录凭证无效:微信开发者工具未返回真实 code,请用小程序绑定的微信账号登录工具并核对 AppID 后重试");
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
            throw new BusinessException(ResultCode.UNAUTHORIZED, "绑定的系统用户已不存在,请重新绑定");
        }
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }
        return user;
    }
}
