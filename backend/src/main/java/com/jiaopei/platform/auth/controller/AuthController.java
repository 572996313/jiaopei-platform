package com.jiaopei.platform.auth.controller;

import com.jiaopei.platform.auth.dto.ChangePasswordRequest;
import com.jiaopei.platform.auth.dto.LoginRequest;
import com.jiaopei.platform.auth.dto.LoginResponse;
import com.jiaopei.platform.auth.service.AuthApplicationService;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.system.dto.MenuView;
import com.jiaopei.platform.system.service.SystemMenuService;
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

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("登录成功", authApplicationService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authApplicationService.logout(resolveToken(request));
        return ApiResponse.success("已退出登录", null);
    }

    @GetMapping("/me")
    public ApiResponse<?> me() {
        return ApiResponse.success("已获取当前用户信息", authApplicationService.currentUser());
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authApplicationService.changePassword(request);
        return ApiResponse.success("密码修改成功", null);
    }

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
