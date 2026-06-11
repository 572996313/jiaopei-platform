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
@Tag(name = "System")
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
        return ApiResponse.success("Login succeeded", authApplicationService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authApplicationService.logout(resolveToken(request));
        return ApiResponse.success("Logout succeeded", null);
    }

    @GetMapping("/me")
    public ApiResponse<?> me() {
        return ApiResponse.success("Current user loaded", authApplicationService.currentUser());
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authApplicationService.changePassword(request);
        return ApiResponse.success("Password changed", null);
    }

    @GetMapping("/current/menus")
    public ApiResponse<List<MenuView>> currentMenus() {
        return ApiResponse.success("Current user menus loaded", systemMenuService.currentUserMenus());
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
