package com.jiaopei.platform.auth.security;

import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.service.AuthApplicationService;
import com.jiaopei.platform.system.service.SystemConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class StubAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthApplicationService authApplicationService;
    private final SystemConfigService systemConfigService;

    public StubAuthenticationFilter(AuthApplicationService authApplicationService,
                                    SystemConfigService systemConfigService) {
        this.authApplicationService = authApplicationService;
        this.systemConfigService = systemConfigService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    AuthenticatedUser user = authApplicationService.authenticateToken(token);
                    List<GrantedAuthority> authorities = user.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList());
                    authorities.addAll(user.permissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList());

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, token, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    Boolean forceChangeEnabled = systemConfigService.getBooleanConfig(
                        AuthApplicationService.FORCE_CHANGE_ENABLED_KEY, true);
                    if (Boolean.TRUE.equals(forceChangeEnabled)
                        && Boolean.TRUE.equals(user.forceChangePassword())
                        && !isPasswordChangeAllowed(request)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "需要先修改密码");
                        return;
                    }
                } catch (AuthenticationException exception) {
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPasswordChangeAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/login")
            || path.equals("/api/auth/me")
            || path.equals("/api/auth/logout")
            || path.equals("/api/auth/change-password")
            || path.equals("/api/health");
    }
}
