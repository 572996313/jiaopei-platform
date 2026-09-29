package com.cornerstone.core.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.config.properties.CornerstoneProperties;
import io.jsonwebtoken.Claims;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class JwtTokenServiceTest {

    private final JwtTokenService jwtTokenService = new JwtTokenService(properties());

    @Test
    void issueThenVerifyReturnsClaims() {
        String token = jwtTokenService.issue(user(), 12, TimeUnit.HOURS);

        Claims claims = jwtTokenService.verify(token);

        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("username", String.class)).isEqualTo("teacher01");
        assertThat(claims.get("orgId", Long.class)).isEqualTo(3L);
    }

    @Test
    void issueProducesUniqueTokenPerLogin() {
        assertThat(jwtTokenService.issue(user(), 12, TimeUnit.HOURS))
            .isNotEqualTo(jwtTokenService.issue(user(), 12, TimeUnit.HOURS));
    }

    @Test
    void verifyRejectsTamperedToken() {
        String token = jwtTokenService.issue(user(), 12, TimeUnit.HOURS);
        // 改签名中段而不是末尾两位:签名本身以 "xx" 结尾时"篡改"无效,用例会随机假失败
        String[] parts = token.split("\\.");
        String signature = parts[2];
        int mid = signature.length() / 2;
        char replaced = signature.charAt(mid) == 'a' ? 'b' : 'a';
        String tampered = parts[0] + "." + parts[1] + "."
            + signature.substring(0, mid) + replaced + signature.substring(mid + 1);

        assertThatThrownBy(() -> jwtTokenService.verify(tampered))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifyRejectsExpiredToken() {
        String token = jwtTokenService.issue(user(), -1, TimeUnit.SECONDS);

        assertThatThrownBy(() -> jwtTokenService.verify(token))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifyRejectsMalformedToken() {
        assertThatThrownBy(() -> jwtTokenService.verify("not-a-jwt"))
            .isInstanceOf(BadCredentialsException.class);
    }

    private static CornerstoneProperties properties() {
        CornerstoneProperties properties = new CornerstoneProperties();
        properties.getAuth().getJwt().setSecret("0123456789abcdef0123456789abcdef-test-secret");
        return properties;
    }

    private static AuthenticatedUser user() {
        return new AuthenticatedUser(
            7L,
            3L,
            "teacher01",
            new LinkedHashSet<>(List.of("EDUCATION_TEACHER")),
            new LinkedHashSet<>(List.of("education:read", "education:attendance:write")),
            false,
            "admin"
        );
    }
}
