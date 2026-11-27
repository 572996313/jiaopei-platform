package com.cornerstone.module.education.miniapp.controller;

import com.cornerstone.core.audit.annotation.OperateLog;
import com.cornerstone.core.audit.domain.ActionType;
import com.cornerstone.core.auth.context.AuthLoginAttributes;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.dto.SelectAccountRequest;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.education.miniapp.dto.WechatLoginRequest;
import com.cornerstone.module.education.miniapp.dto.WechatLoginResponse;
import com.cornerstone.module.education.miniapp.dto.WechatRegisterRequest;
import com.cornerstone.module.education.miniapp.service.WechatAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/wechat")
@Tag(name = "微信小程序认证")
public class WechatAuthController {

    private static final String LOGIN_PROVIDER = "wechat_miniapp";

    private final WechatAuthService wechatAuthService;

    public WechatAuthController(WechatAuthService wechatAuthService) {
        this.wechatAuthService = wechatAuthService;
    }

    @Operation(summary = "小程序微信登录",
        description = "使用 wx.login 获取的 code 登录;只绑一个账号时直接签发令牌,绑多个账号(家长+员工)时返回候选列表与票据,未注册时返回 registered=false")
    // 留痕的操作类型由注解决定,底座不再按接口路径硬编码识别登录
    @OperateLog(actionType = ActionType.LOGIN)
    @PostMapping("/login")
    public ApiResponse<WechatLoginResponse> login(@Valid @RequestBody WechatLoginRequest request,
                                                  HttpServletRequest servletRequest) {
        servletRequest.setAttribute(AuthLoginAttributes.PROVIDER, LOGIN_PROVIDER);
        return ApiResponse.success("登录成功", wechatAuthService.login(request));
    }

    @Operation(summary = "小程序多账号选择登录",
        description = "登录响应命中多个账号时，用返回的票据与账号键选定一个账号并签发令牌")
    @OperateLog(actionType = ActionType.LOGIN)
    @PostMapping("/select")
    public ApiResponse<LoginResponse> select(@Valid @RequestBody SelectAccountRequest request,
                                             HttpServletRequest servletRequest) {
        servletRequest.setAttribute(AuthLoginAttributes.PROVIDER, LOGIN_PROVIDER);
        return ApiResponse.success("登录成功",
            wechatAuthService.select(request.ticket(), request.accountKey()));
    }

    @Operation(summary = "小程序自助注册",
        description = "选择身份(学生/家长)并自动建档进系统,分配对应角色后签发令牌;同一微信重复注册时幂等处理,直接按已注册用户登录")
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody WechatRegisterRequest request) {
        return ApiResponse.success("注册成功", wechatAuthService.register(request));
    }

    @Operation(summary = "绑定当前微信",
        description = "已登录账号(含企业员工账号)把当前微信绑定到本账号;同一微信可绑定多个账号,登录时选账号")
    @PostMapping("/bind")
    public ApiResponse<Void> bind(@Valid @RequestBody WechatLoginRequest request) {
        wechatAuthService.bind(request);
        return ApiResponse.success("微信已绑定", null);
    }

    @Operation(summary = "小程序账号注销(仅限自助注册账号)",
        description = "删除当前微信注册的账号(含身份角色与绑定关系),令牌立即失效;注销后同一微信重新登录为未注册状态,可重新选择身份注册")
    @PostMapping("/deregister")
    public ApiResponse<Void> deregister() {
        wechatAuthService.deregister();
        return ApiResponse.success("账号已注销", null);
    }
}
