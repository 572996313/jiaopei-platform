package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.context.AuthLoginAttributes;
import com.cornerstone.core.auth.dto.AvatarView;
import com.cornerstone.core.auth.dto.ChangePasswordRequest;
import com.cornerstone.core.auth.dto.LoginPageView;
import com.cornerstone.core.auth.dto.LoginRequest;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.auth.service.UserAvatarService;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.MenuView;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.core.system.service.SystemMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "系统管理")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    /** 登录页提示语系统参数键,值由部署方在自己的迁移里种下,底座只读不写 */
    private static final String LOGIN_PAGE_TIP_KEY = "auth.login.page_tip";

    private final AuthApplicationService authApplicationService;
    private final SystemMenuService systemMenuService;
    private final SystemConfigService systemConfigService;
    private final UserAvatarService userAvatarService;

    public AuthController(AuthApplicationService authApplicationService, SystemMenuService systemMenuService,
                          SystemConfigService systemConfigService, UserAvatarService userAvatarService) {
        this.authApplicationService = authApplicationService;
        this.systemMenuService = systemMenuService;
        this.systemConfigService = systemConfigService;
        this.userAvatarService = userAvatarService;
    }

    @Operation(summary = "登录", description = "账号密码登录,返回 JWT 访问令牌")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest servletRequest) {
        LoginResponse response = authApplicationService.login(request);
        servletRequest.setAttribute(AuthLoginAttributes.PROVIDER, "password");
        return ApiResponse.success("登录成功", response);
    }

    @Operation(summary = "获取登录页展示信息", description = "无需登录,返回配置在系统参数里的登录页提示语,空串表示不展示")
    @GetMapping("/login-page")
    public ApiResponse<LoginPageView> loginPage() {
        return ApiResponse.success("已获取登录页信息",
            new LoginPageView(systemConfigService.getConfigValue(LOGIN_PAGE_TIP_KEY, "")));
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

    // 头像两条只改调用者自己的账号：类级路径在 /api/auth 下，按 ArchUnit 口径免挂 @RequiresPermission，
    // 登录态由 UserAvatarService 里的 AuthContext.requireUser() 兜住。

    @Operation(summary = "上传本人头像",
        description = "multipart 提交，只接受 JPG/PNG；服务端解码校验后立即生效，旧头像文件解绑后由孤儿清理回收")
    @PostMapping("/me/avatar")
    public ApiResponse<AvatarView> updateAvatar(
            @Parameter(description = "头像图片") @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.success("头像已更新", new AvatarView(userAvatarService.updateAvatar(file)));
    }

    @Operation(summary = "清除本人头像", description = "清除后前端回落为显示名称首字；旧头像文件解绑后由孤儿清理回收")
    @DeleteMapping("/me/avatar")
    public ApiResponse<Void> clearAvatar() {
        userAvatarService.clearAvatar();
        return ApiResponse.success("头像已清除", null);
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
