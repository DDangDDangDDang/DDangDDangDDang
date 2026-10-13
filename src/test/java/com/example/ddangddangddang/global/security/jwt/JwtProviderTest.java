package com.example.ddangddangddang.global.security.jwt;

import com.example.ddangddangddang.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItZGRhbmctZGRhbmctZGRhbmctdGVzdA==";

    private final JwtProvider jwtProvider = new JwtProvider(
            new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14), false)
    );

    @Test
    @DisplayName("Access Token에는 회원 정보와 ACCESS 타입이 담긴다")
    void createAccessToken() {
        User user = createUser(1L);

        Claims claims = jwtProvider.parseClaims(jwtProvider.createAccessToken(user));

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get(JwtProvider.CLAIM_TYPE)).isEqualTo("ACCESS");
        assertThat(claims.get(JwtProvider.CLAIM_EMAIL)).isEqualTo("user@test.com");
        assertThat(claims.get(JwtProvider.CLAIM_NICKNAME)).isEqualTo("경매왕");
        assertThat(claims.get(JwtProvider.CLAIM_ROLE)).isEqualTo("USER");
    }

    @Test
    @DisplayName("Refresh Token에는 회원 ID와 REFRESH 타입만 담긴다")
    void createRefreshToken() {
        Claims claims = jwtProvider.parseClaims(jwtProvider.createRefreshToken(1L));

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(JwtProvider.CLAIM_TYPE)).isEqualTo("REFRESH");
        assertThat(claims.get(JwtProvider.CLAIM_EMAIL)).isNull();
    }

    @Test
    @DisplayName("만료된 토큰은 ExpiredJwtException을 던진다")
    void expiredToken() {
        JwtProvider expiredProvider = new JwtProvider(
                new JwtProperties(SECRET, Duration.ofMillis(-1000), Duration.ofDays(14), false)
        );
        String token = expiredProvider.createAccessToken(createUser(1L));

        assertThatThrownBy(() -> jwtProvider.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 JwtException을 던진다")
    void invalidSignature() {
        JwtProvider otherProvider = new JwtProvider(new JwtProperties(
                "b3RoZXItand0LXNlY3JldC1rZXktZm9yLWRkYW5nLWRkYW5nLWRkYW5nLXRlc3Q=",
                Duration.ofMinutes(30), Duration.ofDays(14), false
        ));
        String token = otherProvider.createAccessToken(createUser(1L));

        assertThatThrownBy(() -> jwtProvider.parseClaims(token))
                .isInstanceOf(JwtException.class);
    }

    private User createUser(Long id) {
        User user = User.create("user@test.com", "encoded", "경매왕");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}