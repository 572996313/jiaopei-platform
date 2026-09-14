package com.jiaopei.platform.auth.controller;

import com.jiaopei.platform.auth.dto.LoginResponse;
import com.jiaopei.platform.auth.dto.WechatLoginRequest;
import com.jiaopei.platform.auth.dto.WechatLoginResponse;
import com.jiaopei.platform.auth.dto.WechatRegisterRequest;
import com.jiaopei.platform.auth.service.WechatAuthService;
import com.jiaopei.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/wechat")
@Tag(name = "微信小程序认证")
public class WechatAuthController {

    private final WechatAuthService wechatAuthService;

    public WechatAuthController(WechatAuthService wechatAuthService) {
        this.wechatAuthService = wechatAuthService;
    }

    @Operation(summary = "小程序微信登录",
        description = "使用 wx.login 获取的 code 登录;openid 已注册时直接签发令牌,未注册时返回 registered=false,由前端引导进入身份选择注册页")
    @PostMapping("/login")
    public ApiResponse<WechatLoginResponse> login(@Valid @RequestBody WechatLoginRequest request) {
        return ApiResponse.success("登录成功", wechatAuthService.login(request));
    }

    @Operation(summary = "小程序自助注册",
        description = "选择身份(学生/家长)并自动建档进系统,分配对应角色后签发令牌;同一微信重复注册时幂等处理,直接按已注册用户登录")
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody WechatRegisterRequest request) {
        return ApiResponse.success("注册成功", wechatAuthService.register(request));
    }

    @Operation(summary = "小程序账号注销(仅限自助注册账号)",
        description = "删除当前微信注册的账号(含身份角色与绑定关系),令牌立即失效;注销后同一微信重新登录为未注册状态,可重新选择身份注册")
    @PostMapping("/deregister")
    public ApiResponse<Void> deregister() {
        wechatAuthService.deregister();
        return ApiResponse.success("账号已注销", null);
    }
}
