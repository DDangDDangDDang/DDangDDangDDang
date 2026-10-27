package com.example.ddangddangddang.domain.auth.oauth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 구글 로그인 설정. 키가 없어도 서버는 뜬다. 키 없이 구글 로그인을 호출하면 OAUTH_005로 응답한다.
 *
 * @param clientId     Google Cloud 콘솔 > API 및 서비스 > 사용자 인증 정보 > OAuth 클라이언트 ID
 * @param clientSecret 같은 화면의 클라이언트 보안 비밀번호 (구글은 웹 클라이언트에 필수)
 * @param redirectUri  콘솔의 "승인된 리디렉션 URI"에 등록한 값. 프론트가 구글 로그인 페이지에 보낸 값과 같아야 한다
 */
@ConfigurationProperties(prefix = "app.oauth.google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String tokenUrl,
        String userInfoUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public GoogleOAuthProperties {
        if (!StringUtils.hasText(tokenUrl)) {
            tokenUrl = "https://oauth2.googleapis.com/token";
        }
        if (!StringUtils.hasText(userInfoUrl)) {
            userInfoUrl = "https://openidconnect.googleapis.com/v1/userinfo";
        }
        // 타임아웃이 없으면 구글 장애 시 요청 스레드가 무한정 대기한다
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret) && StringUtils.hasText(redirectUri);
    }
}