package com.jiaopei.platform.auth.controller;

import com.jiaopei.platform.auth.dto.LoginResponse;
import com.jiaopei.platform.auth.dto.WechatBindRequest;
import com.jiaopei.platform.auth.dto.WechatLoginRequest;
import com.jiaopei.platform.auth.dto.WechatLoginResponse;
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
        description = "使用 wx.login 获取的 code 登录;openid 已绑定用户时直接签发令牌,未绑定时返回 bound=false,由前端引导进入绑定页")
    @PostMapping("/login")
    public ApiResponse<WechatLoginResponse> login(@Valid @RequestBody WechatLoginRequest request) {
        return ApiResponse.success("登录成功", wechatAuthService.login(request));
    }

    @Operation(summary = "绑定微信与系统账号",
        description = "校验系统账号密码后,将当前微信 openid 与该用户绑定并签发令牌;同一微信重复绑定会改绑到新账号")
    @PostMapping("/bind")
    public ApiResponse<LoginResponse> bind(@Valid @RequestBody WechatBindRequest request) {
        return ApiResponse.success("绑定成功", wechatAuthService.bind(request));
    }
}
