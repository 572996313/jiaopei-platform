package com.cornerstone.core.auth.security;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.system.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
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
    private final ObjectMapper objectMapper;

    public StubAuthenticationFilter(AuthApplicationService authApplicationService,
                                    SystemConfigService systemConfigService,
                                    ObjectMapper objectMapper) {
        this.authApplicationService = authApplicationService;
        this.systemConfigService = systemConfigService;
        this.objectMapper = objectMapper;
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
                        // 这里必须直接写响应体,不能 sendError:sendError 会触发一次到 /error 的
                        // ERROR 派发,而该派发既不走本过滤器、/error 又不在免登录清单里,
                        // 最终会被 SecurityConfig 再拦成 401,把"需要先改密码"伪装成"未登录或登录已失效"。
                        writeForceChangeRequired(response);
                        return;
                    }
                } catch (AuthenticationException exception) {
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeForceChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
            ApiResponse.failure(ResultCode.FORBIDDEN, "需要先修改密码"));
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
