package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.dto.AccountActivateRequest;
import com.cornerstone.core.auth.dto.InvitationPrecheckView;
import com.cornerstone.core.auth.dto.InvitationRegisterRequest;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开注册入口(挂在 /api/auth 前缀下,由 public-paths 通配放行、ArchUnit 按前缀豁免权限注解)。
 * 凭证决定机构与角色,注册/激活成功后直接签发令牌(免二次登录)。
 */
@RestController
@RequestMapping("/api/auth/register")
@Tag(name = "账号注册")
public class AuthRegisterController {

    private final AuthInvitationService authInvitationService;

    public AuthRegisterController(AuthInvitationService authInvitationService) {
        this.authInvitationService = authInvitationService;
    }

    @Operation(summary = "注册凭证预检", description = "无需登录,按凭证码返回类型与机构/角色信息,供注册页展示;不泄露账号明细")
    @GetMapping("/precheck")
    public ApiResponse<InvitationPrecheckView> precheck(
        @Parameter(description = "邀请码或激活码") @RequestParam String code) {
        return ApiResponse.success("凭证已校验", authInvitationService.precheck(code));
    }

    @Operation(summary = "邀请码注册", description = "凭邀请码自助注册账号,自动归属凭证指定机构并分配角色,注册成功直接返回登录令牌")
    @PostMapping("/invitation")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody InvitationRegisterRequest request) {
        return ApiResponse.success("注册成功", authInvitationService.register(request));
    }

    @Operation(summary = "激活预建账号", description = "凭激活码为待激活账号自设密码并启用,激活成功直接返回登录令牌")
    @PostMapping("/activation")
    public ApiResponse<LoginResponse> activate(@Valid @RequestBody AccountActivateRequest request) {
        return ApiResponse.success("账号已激活", authInvitationService.activate(request));
    }
}
