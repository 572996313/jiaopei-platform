package com.jiaopei.platform.support;

import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.dto.ChangePasswordRequest;
import com.jiaopei.platform.auth.dto.CurrentUserResponse;
import com.jiaopei.platform.auth.dto.LoginRequest;
import com.jiaopei.platform.auth.dto.LoginResponse;
import com.jiaopei.platform.auth.service.AuthApplicationService;
import com.jiaopei.platform.config.properties.JiaopeiProperties;
import com.jiaopei.platform.system.dto.MenuCreateRequest;
import com.jiaopei.platform.system.dto.MenuUpdateRequest;
import com.jiaopei.platform.system.dto.MenuView;
import com.jiaopei.platform.system.dto.RoleMenuUpdateRequest;
import com.jiaopei.platform.system.service.SystemMenuService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
@EnableConfigurationProperties(JiaopeiProperties.class)
public class WebMvcSecurityTestConfig {

    @Bean
    public AuthApplicationService authApplicationService() {
        return new AuthApplicationService() {
            @Override
            public LoginResponse login(LoginRequest request) {
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
                    false
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
                    null,
                    user.username(),
                    "平台管理员",
                    new ArrayList<>(user.roles()),
                    new ArrayList<>(user.permissions()),
                    false,
                    false
                );
            }

            @Override
            public void changePassword(ChangePasswordRequest request) {
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
                    false
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
            public List<Long> updateRoleMenus(Long roleId, RoleMenuUpdateRequest request) {
                return request.menuIds();
            }

            @Override
            public List<MenuView> currentUserMenus() {
                return List.of();
            }
        };
    }
}
