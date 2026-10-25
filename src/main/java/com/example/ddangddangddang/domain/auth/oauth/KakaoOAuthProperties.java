package com.example.ddangddangddang.domain.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 카카오 로그인 설정
 * 키 없이 카카오 로그인을 호출하면 OAUTH_005 응답
 *
 * @param clientId     REST API key
 * @param clientSecret 카카오 콘솔 > 보안 > Client Secret
 * @param redirectUri  카카오 콘솔에 등록한 Redirect URI
 */
@ConfigurationProperties(prefix = "app.oauth.kakao")
public record KakaoOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authBaseUrl,
        String apiBaseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public KakaoOAuthProperties {
        if (!StringUtils.hasText(authBaseUrl)) {
            authBaseUrl = "https://kauth.kakao.com";
        }
        if (!StringUtils.hasText(apiBaseUrl)) {
            apiBaseUrl = "https://kapi.kakao.com";
        }
        // 타임아웃이 없으면 카카오 장애 시 오청,스레드가 무한정 대기한다.
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(redirectUri);
    }
}