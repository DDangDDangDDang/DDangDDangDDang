package com.example.ddangddangddang.global.security.jwt;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Refresh Token 쿠키를 만든다.
 * HttpOnly로 JS 접근을 막고, Path를 /api/v1/auth로 제한해 일반 API 요청에는 실려 가지 않게 한다.
 */
@Component
public class RefreshTokenCookieProvider {

    public static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshTokenCookieProvider(JwtProperties properties) {
        this.secure = properties.cookieSecure();
        this.maxAge = properties.refreshTokenValidity();
    }

    public ResponseCookie create(String refreshToken) {
        return baseCookie(refreshToken).maxAge(maxAge).build();
    }

    public ResponseCookie expire() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}