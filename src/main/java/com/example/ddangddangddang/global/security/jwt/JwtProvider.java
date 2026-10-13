package com.example.ddangddangddang.global.security.jwt;

import com.example.ddangddangddang.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtProvider {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NICKNAME = "nickname";
    public static final String CLAIM_ROLE = "role";
    private static final String BEARER_PREFIX = "Bearer ";


    private final SecretKey secretKey;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    public JwtProvider(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.accessTokenValidity = properties.accessTokenValidity();
        this.refreshTokenValidity = properties.refreshTokenValidity();
    }

    public String createAccessToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TokenType.ACCESS.name())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NICKNAME, user.getNickname())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenValidity.toMillis()))
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TokenType.REFRESH.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenValidity.toMillis()))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 서명과 만료를 검증하고 Claims를 반환한다.
     * 만료되면 ExpiredJwtException, 그 밖의 위변조·형식 오류는 JwtException을 던진다.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Duration getAccessTokenValidity() {
        return accessTokenValidity;
    }

    public Duration getRefreshTokenValidity() {
        return refreshTokenValidity;
    }

    /**
     * "Bearer {token}" 형식의 Authorization 헤더에서 토큰만 꺼낸다. 형식이 다르면 null.
     */
    public static String resolveBearerToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length());
            return token.isBlank() ? null : token;
        }
        return null;
    }
}