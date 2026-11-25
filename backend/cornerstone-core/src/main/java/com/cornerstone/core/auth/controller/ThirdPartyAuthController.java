package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.context.AuthLoginAttributes;
import com.cornerstone.core.auth.dto.AccountSelectionView;
import com.cornerstone.core.auth.dto.AuthProviderView;
import com.cornerstone.core.auth.dto.BindTicketView;
import com.cornerstone.core.auth.dto.ExchangeTicketRequest;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.dto.SelectAccountRequest;
import com.cornerstone.core.auth.service.ThirdPartyLoginService;
import com.cornerstone.core.audit.annotation.OperateLog;
import com.cornerstone.core.audit.domain.ActionType;
import com.cornerstone.core.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网页端第三方登录入口。浏览器可直达的只有授权跳转与回调两条 GET，
 * 令牌一律通过一次性票据 POST 换取，不落在地址栏。
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "系统管理")
public class ThirdPartyAuthController {

    private final ThirdPartyLoginService thirdPartyLoginService;

    public ThirdPartyAuthController(ThirdPartyLoginService thirdPartyLoginService) {
        this.thirdPartyLoginService = thirdPartyLoginService;
    }

    @Operation(summary = "获取登录页第三方渠道", description = "无需登录；只返回启用中且已有协议实现的渠道")
    @GetMapping("/providers")
    public ApiResponse<List<AuthProviderView>> providers() {
        return ApiResponse.success("已获取第三方登录渠道", thirdPartyLoginService.listLoginProviders());
    }

    @Operation(summary = "发起第三方登录授权",
        description = "302 跳转到渠道授权页；bindTicket 为空表示登录，不为空表示给当前账号绑定")
    @GetMapping("/oauth2/{providerCode}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String providerCode,
                                          @RequestParam(required = false) String bindTicket) {
        return redirect(thirdPartyLoginService.buildAuthorizeUrl(providerCode, bindTicket));
    }

    @Operation(summary = "第三方登录回调",
        description = "校验 state 后换取外部身份，再 302 回前端换票/选账号页面；失败回登录页并带 oauthError")
    @GetMapping("/oauth2/{providerCode}/callback")
    public ResponseEntity<Void> callback(@PathVariable String providerCode,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpServletRequest request) {
        return redirect(thirdPartyLoginService.handleCallback(providerCode, code, state, error, request));
    }

    @Operation(summary = "第三方登录换取令牌", description = "用回调页拿到的一次性票据换取访问令牌")
    @OperateLog(actionType = ActionType.LOGIN)
    @PostMapping("/oauth2/exchange")
    public ApiResponse<LoginResponse> exchange(@Valid @RequestBody ExchangeTicketRequest request,
                                               HttpServletRequest servletRequest) {
        ThirdPartyLoginService.LoginResult result = thirdPartyLoginService.exchange(request.ticket());
        servletRequest.setAttribute(AuthLoginAttributes.PROVIDER, result.providerCode());
        return ApiResponse.success("登录成功", result.login());
    }

    @Operation(summary = "获取第三方登录可选账号", description = "多账号命中时用于渲染账号选择页")
    @PostMapping("/oauth2/select-options")
    public ApiResponse<AccountSelectionView> selectOptions(@Valid @RequestBody ExchangeTicketRequest request) {
        return ApiResponse.success("已获取可选账号", thirdPartyLoginService.selectOptions(request.ticket()));
    }

    @Operation(summary = "选择账号登录", description = "从可选账号中选定一个，签发该账号的访问令牌")
    @OperateLog(actionType = ActionType.LOGIN)
    @PostMapping("/oauth2/select")
    public ApiResponse<LoginResponse> select(@Valid @RequestBody SelectAccountRequest request,
                                             HttpServletRequest servletRequest) {
        ThirdPartyLoginService.LoginResult result =
            thirdPartyLoginService.select(request.ticket(), request.accountKey());
        servletRequest.setAttribute(AuthLoginAttributes.PROVIDER, result.providerCode());
        return ApiResponse.success("登录成功", result.login());
    }

    @Operation(summary = "申请第三方账号绑定票", description = "已登录用户给当前账号绑定第三方身份，绑定票一次性有效")
    @PostMapping("/oauth2/{providerCode}/bind-ticket")
    public ApiResponse<BindTicketView> bindTicket(@PathVariable String providerCode) {
        return ApiResponse.success("已生成绑定票据", thirdPartyLoginService.createBindTicket(providerCode));
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
