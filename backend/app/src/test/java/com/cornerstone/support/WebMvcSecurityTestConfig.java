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
            public void invalidateUserSessions(Long userId) {
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
                        "education:write",
                        "system:user:manage",
                        "system:role:manage",
                        "system:menu:manage",
                        "system:dict:manage"
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
