package com.cornerstone.core.auth.security;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.config.properties.CornerstoneProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

/**
 * 签发与校验 HS256 JWT 访问令牌。
 * 令牌自包含身份声明且带过期时间,但服务端仍以 Redis 会话为准:
 * 登出或管理员踢人后,即使 JWT 未过期也会立即失效。
 */
@Component
public class JwtTokenService {

    private final SecretKey secretKey;

    public JwtTokenService(CornerstoneProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(
            properties.getAuth().getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(AuthenticatedUser user, long duration, TimeUnit unit) {
        Instant now = Instant.now();
        return Jwts.builder()
            .id(UUID.randomUUID().toString().replace("-", ""))
            .subject(String.valueOf(user.userId()))
            .claim("username", user.username())
            .claim("orgId", user.orgId())
            .claim("roles", List.copyOf(user.roles()))
            .claim("permissions", List.copyOf(user.permissions()))
            .claim("forceChangePassword", user.forceChangePassword())
            .claim("portal", user.portal())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(unit.toMillis(duration))))
            .signWith(secretKey)
            .compact();
    }

    public Claims verify(String token) {
        try {
            return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException("访问令牌无效或已过期", exception);
        }
    }
}
