package com.example.ddangddangddang.domain.notification.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 웹 푸시(브라우저 알림) 설정. 모든 값에 기본값이 있어 application.yml에 적지 않아도 동작한다.
 *
 * <pre>
 * web-push.enabled        보내기 사용 여부. 기본 true (테스트 설정에서는 false)
 * web-push.public-key     VAPID 공개키 (base64url, 65바이트)
 * web-push.private-key    VAPID 개인키 (base64url, 32바이트). 둘 중 하나라도 비면 서버를 켤 때마다 새로 만든다.
 *                         (로컬 개발용. 키가 바뀌면 브라우저가 다음 접속 때 다시 구독한다. 운영에서는 반드시 고정 키를 넣는다)
 * web-push.subject        푸시 서비스가 연락할 주소 (mailto: 또는 https:)
 * web-push.ttl            푸시 서비스가 꺼진 브라우저를 위해 보관하는 시간. 기본 30분 (방송 시작·마감 임박은 늦게 받으면 의미가 없다)
 * web-push.require-keys   키가 비어 있으면 서버를 띄우지 않는다. 기본 false (운영 설정에서 true. 빈 문자열로 채운 경우도 막기 위해)
 * web-push.allowed-hosts  구독 주소로 받을 푸시 서비스 호스트(접미사). 서버가 아무 주소로나 요청을 보내지 않도록 제한한다.
 * </pre>
 */
@ConfigurationProperties(prefix = "web-push")
public record WebPushProperties(Boolean enabled, String publicKey, String privateKey, String subject, Duration ttl,
                                List<String> allowedHosts, Boolean requireKeys) {

    /** 크롬(FCM)·파이어폭스(Mozilla)·엣지(WNS)·사파리(Apple) */
    static final List<String> DEFAULT_ALLOWED_HOSTS = List.of(
            "fcm.googleapis.com", "updates.push.services.mozilla.com", ".notify.windows.com", ".push.apple.com");

    public WebPushProperties {
        enabled = enabled == null || enabled;
        requireKeys = requireKeys != null && requireKeys;
        subject = subject == null || subject.isBlank() ? "mailto:no-reply@ddangddangddang.com" : subject;
        ttl = ttl == null ? Duration.ofMinutes(30) : ttl;
        allowedHosts = allowedHosts == null || allowedHosts.isEmpty() ? DEFAULT_ALLOWED_HOSTS : List.copyOf(allowedHosts);
    }
}
