package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.dto.ChangePasswordRequest;
import com.cornerstone.core.auth.dto.LoginRequest;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.MenuView;
import com.cornerstone.core.system.service.SystemMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "系统管理")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthApplicationService authApplicationService;
    private final SystemMenuService systemMenuService;

    public AuthController(AuthApplicationService authApplicationService, SystemMenuService systemMenuService) {
        this.authApplicationService = authApplicationService;
        this.systemMenuService = systemMenuService;
    }

    @Operation(summary = "登录", description = "账号密码登录,返回 JWT 访问令牌")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("登录成功", authApplicationService.login(request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authApplicationService.logout(resolveToken(request));
        return ApiResponse.success("已退出登录", null);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResponse<?> me() {
        return ApiResponse.success("已获取当前用户信息", authApplicationService.currentUser());
    }

    @Operation(summary = "修改密码")
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authApplicationService.changePassword(request);
        return ApiResponse.success("密码修改成功", null);
    }

    @Operation(summary = "获取当前用户菜单")
    @GetMapping("/current/menus")
    public ApiResponse<List<MenuView>> currentMenus() {
        return ApiResponse.success("已获取当前用户菜单", systemMenuService.currentUserMenus());
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
