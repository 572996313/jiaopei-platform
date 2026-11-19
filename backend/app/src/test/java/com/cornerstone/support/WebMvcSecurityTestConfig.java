package com.cornerstone.support;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.dto.ChangePasswordRequest;
import com.cornerstone.core.auth.dto.CurrentUserResponse;
import com.cornerstone.core.auth.dto.LoginRequest;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.config.properties.CornerstoneProperties;
import com.cornerstone.core.system.dto.MenuCreateRequest;
import com.cornerstone.core.system.dto.MenuUpdateRequest;
import com.cornerstone.core.system.dto.MenuView;
import com.cornerstone.core.system.dto.RoleAuthorizationView;
import com.cornerstone.core.system.dto.RoleMenuUpdateRequest;
import com.cornerstone.core.system.service.SystemMenuService;
import com.cornerstone.core.system.service.SystemConfigService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.BadCredentialsException;

@TestConfiguration
@EnableConfigurationProperties(CornerstoneProperties.class)
public class WebMvcSecurityTestConfig {

    @Bean
    public AuthApplicationService authApplicationService() {
        return new AuthApplicationService() {
            @Override
            public LoginResponse login(LoginRequest request) {
                if ("wrong-password".equals(request.password())) {
                    throw new BadCredentialsException("用户名或密码错误");
                }
                AuthenticatedUser user = stubUser();
                return new LoginResponse(
                    user.userId(),
                    user.orgId(),
                    request.username(),
                    "平台管理员",
                    new ArrayList<>(user.roles()),
                    new ArrayList<>(user.permissions()),
                    "Bearer",
                    "stub-access-token",
                    false,
                    user.portal()
                );
            }

            @Override
            public void logout(String accessToken) {
            }

            @Override
            public AuthenticatedUser authenticateToken(String accessToken) {
                return stubUser();
            }

            @Override
            public CurrentUserResponse currentUser() {
                AuthenticatedUser user = stubUser();
                return new CurrentUserResponse(
                    user.userId(),
                    user.orgId(),
                    user.orgId(),
                    user.username(),
                    "平台管理员",
                    new ArrayList<>(user.roles()),
                    new ArrayList<>(user.permissions()),
                    false,
                    false,
                    user.portal()
                );
            }

            @Override
            public void changePassword(ChangePasswordRequest request) {
            }

            @Override
            public void updateDisplayName(Long userId, String displayName) {
            }

            @Override
            public void invalidateUserSessions(Long userId) {
            }

            @Override
            public void invalidateUserSessionsByRole(Long roleId) {
            }

            private AuthenticatedUser stubUser() {
                return new AuthenticatedUser(
                    1L,
                    1L,
                    "admin",
                    new LinkedHashSet<>(List.of("PLATFORM_ADMIN")),
                    new LinkedHashSet<>(List.of(
                        "organization:read",
                        "organization:write",
                        "education:read",
                        // 教务写码按页面域拆分（V74），stub 全授以免切片测试被 403 挡掉
                        "education:schedule:write",
                        "education:attendance:write",
                        "education:attendance:manage",
                        "education:class:write",
                        "education:profile:write",
                        "education:catalog:write",
                        "system:user:manage",
                        "system:role:manage",
                        "system:menu:manage",
                        "system:dict:manage",
                        // 机构=租户属底座，权限码与业务的 organization:* 分开（阶段五-0c）
                        "tenant:read",
                        "tenant:write"
                    )),
                    false,
                    "admin"
                );
            }
        };
    }

    @Bean
    public SystemMenuService systemMenuService() {
        return new SystemMenuService() {
            @Override
            public List<MenuView> tree() {
                return List.of();
            }

            @Override
            public List<MenuView> listMenus() {
                return List.of();
            }

            @Override
            public MenuView createMenu(MenuCreateRequest request) {
                return null;
            }

            @Override
            public MenuView updateMenu(Long id, MenuUpdateRequest request) {
                return null;
            }

            @Override
            public void deleteMenu(Long id) {
            }

            @Override
            public List<Long> getRoleMenuIds(Long roleId) {
                return List.of();
            }

            @Override
            public Set<String> getPermissionCodesByRoleIds(List<Long> roleIds) {
                return Set.of();
            }

            @Override
            public List<Long> updateRoleMenus(Long roleId, RoleMenuUpdateRequest request) {
                return request.menuIds();
            }

            @Override
            public RoleAuthorizationView getRoleAuthorization(Long roleId) {
                return new RoleAuthorizationView(roleId, List.of(), List.of());
            }

            @Override
            public RoleAuthorizationView updateRoleAuthorization(Long roleId, RoleMenuUpdateRequest request) {
                return new RoleAuthorizationView(roleId, request.menuIds(), List.of());
            }

            @Override
            public List<MenuView> currentUserMenus() {
                return List.of();
            }
        };
    }

    @Bean
    public SystemConfigService systemConfigService() {
        return new SystemConfigService() {
            @Override
            public List<com.cornerstone.core.system.dto.SystemConfigView> listConfigs() {
                return List.of();
            }

            @Override
            public com.cornerstone.core.common.api.PageResult<com.cornerstone.core.system.dto.SystemConfigView> pageConfigs(
                com.cornerstone.core.system.dto.SystemConfigPageQuery query) {
                return new com.cornerstone.core.common.api.PageResult<>(List.of(), 0, query.getPage(), query.getPageSize());
            }

            @Override
            public com.cornerstone.core.system.dto.SystemConfigView getConfig(Long id) {
                return null;
            }

            @Override
            public String getConfigValue(String key, String defaultValue) {
                if ("auth.password.force_change_enabled".equals(key)) {
                    return "false";
                }
                if ("auth.login.page_tip".equals(key)) {
                    return "默认管理员账号 admin";
                }
                // 品牌参数：只 stub 三条，brand.login_copy 故意不配，用来断言"未配置返回空串"
                if ("brand.name".equals(key)) {
                    return "测试平台";
                }
                if ("brand.tagline".equals(key)) {
                    return "测试用定位副标题";
                }
                if ("brand.login_form_title".equals(key)) {
                    return "进入测试平台";
                }
                return defaultValue;
            }

            @Override
            public Boolean getBooleanConfig(String key, Boolean defaultValue) {
                if ("auth.password.force_change_enabled".equals(key)) {
                    return false;
                }
                return defaultValue;
            }

            @Override
            public Integer getIntConfig(String key, Integer defaultValue) {
                return defaultValue;
            }

            @Override
            public com.cornerstone.core.system.dto.SystemConfigView createConfig(com.cornerstone.core.system.dto.SystemConfigCreateRequest request) {
                return null;
            }

            @Override
            public com.cornerstone.core.system.dto.SystemConfigView updateConfig(Long id, com.cornerstone.core.system.dto.SystemConfigUpdateRequest request) {
                return null;
            }

            @Override
            public void deleteConfig(Long id) {
            }
        };
    }
}
