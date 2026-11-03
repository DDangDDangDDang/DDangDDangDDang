package com.example.ddangddangddang.global.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieProviderTest {

    private static final String SECRET = "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItZGRhbmctZGRhbmctZGRhbmctdGVzdA==";
    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(14);

    @Test
    @DisplayName("Refresh Token 쿠키는 HttpOnly, SameSite=Strict, 인증 API 경로로 제한되고 Refresh Token 유효기간만큼 유지된다")
    void create() {
        RefreshTokenCookieProvider provider = createProvider(false);

        ResponseCookie cookie = provider.create("refresh-token");

        assertThat(cookie.getName()).isEqualTo(RefreshTokenCookieProvider.COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo("refresh-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(REFRESH_TOKEN_VALIDITY);
    }

    @Test
    @DisplayName("만료 쿠키는 값이 비어 있고 Max-Age가 0이며 같은 경로로 발급된다")
    void expire() {
        RefreshTokenCookieProvider provider = createProvider(false);

        ResponseCookie cookie = provider.expire();

        assertThat(cookie.getName()).isEqualTo(RefreshTokenCookieProvider.COOKIE_NAME);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("cookieSecure 설정에 따라 Secure 속성이 결정된다")
    void secure() {
        assertThat(createProvider(true).create("refresh-token").isSecure()).isTrue();
        assertThat(createProvider(true).expire().isSecure()).isTrue();
        assertThat(createProvider(false).create("refresh-token").isSecure()).isFalse();
    }

    private RefreshTokenCookieProvider createProvider(boolean cookieSecure) {
        return new RefreshTokenCookieProvider(
                new JwtProperties(SECRET, Duration.ofMinutes(30), REFRESH_TOKEN_VALIDITY, cookieSecure)
        );
    }
}
