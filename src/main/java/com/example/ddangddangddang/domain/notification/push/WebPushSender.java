package com.example.ddangddangddang.domain.notification.push;

import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationContent;
import com.example.ddangddangddang.domain.notification.repository.NotificationPreferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 저장된 알림을 그 회원이 브라우저 알림을 켠 모든 기기로 보낸다.
 * 탭을 닫아도 브라우저가 서비스 워커(frontend/public/sw.js)를 깨워 알림을 띄운다.
 *
 * - 요청은 비동기로 보내고 기다리지 않는다. (알림 저장·실시간 전송을 늦추지 않게)
 * - 푸시 서비스가 404·410으로 답하면 브라우저가 구독을 끊은 것이므로 지운다.
 * - 실패는 로그만 남긴다. 알림함에는 이미 저장돼 있다.
 */
@Slf4j
@Component
public class WebPushSender {

    /** VAPID 토큰 유효 시간. 표준 최대 24시간보다 짧게 둔다 */
    private static final Duration TOKEN_VALIDITY = Duration.ofHours(12);

    private final PushSubscriptionRepository repository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final VapidKeys vapidKeys;
    private final WebPushProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;

    @Autowired
    public WebPushSender(PushSubscriptionRepository repository, NotificationPreferenceRepository preferenceRepository,
                         VapidKeys vapidKeys, WebPushProperties properties, ObjectMapper objectMapper) {
        this(repository, preferenceRepository, vapidKeys, properties, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), Clock.systemUTC());
    }

    WebPushSender(PushSubscriptionRepository repository, NotificationPreferenceRepository preferenceRepository,
                  VapidKeys vapidKeys, WebPushProperties properties, ObjectMapper objectMapper, HttpClient httpClient, Clock clock) {
        this.repository = repository;
        this.preferenceRepository = preferenceRepository;
        this.vapidKeys = vapidKeys;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    public void send(List<Notification> notifications) {
        if (!properties.enabled() || notifications.isEmpty()) {
            return;
        }
        List<Long> userIds = notifications.stream().map(Notification::getUserId).distinct().toList();
        Map<Long, List<PushSubscription>> byUser = repository.findByUserIdIn(userIds)
                .stream().collect(Collectors.groupingBy(PushSubscription::getUserId));
        if (byUser.isEmpty()) {
            return;
        }
        Set<Long> showWhenVisible = new HashSet<>(preferenceRepository.findPushWhenVisibleUserIds(byUser.keySet()));
        for (Notification notification : notifications) {
            List<PushSubscription> subscriptions = byUser.getOrDefault(notification.getUserId(), List.of());
            if (subscriptions.isEmpty()) {
                continue;
            }
            byte[] payload = payload(notification, showWhenVisible.contains(notification.getUserId()));
            for (PushSubscription subscription : subscriptions) {
                try {
                    send(subscription, payload);
                } catch (RuntimeException e) {
                    log.warn("웹 푸시를 보내지 못했습니다. subscriptionId={}", subscription.getId(), e);
                }
            }
        }
    }

    /**
     * 서비스 워커가 받는 내용: 알림 제목·내용, 누르면 열 경로, 같은 알림을 한 번만 띄우기 위한 tag
     * 푸시 본문 한도(WebPushCrypto.MAX_PLAINTEXT)를 넘으면 내용(body)을 줄인다. (알림 문구는 짧아 보통은 그대로)
     * showWhenVisible: 회원이 "사이트를 보고 있을 때도 브라우저 알림 받기"를 켰는지. 끄면 서비스 워커는 보고 있는 탭이 있을 때 띄우지 않는다.
     */
    byte[] payload(Notification notification, boolean showWhenVisible) {
        NotificationContent content = NotificationContent.of(notification);
        String body = content.body() == null ? "" : content.body();
        byte[] bytes = payload(notification, content, body, showWhenVisible);
        while (bytes.length > WebPushCrypto.MAX_PLAINTEXT && !body.isEmpty()) {
            int excess = bytes.length - WebPushCrypto.MAX_PLAINTEXT;
            body = body.substring(0, Math.max(0, body.length() - Math.max(1, excess / 3) - 1)) + "…";
            if (body.length() <= 1) {
                body = "";
            }
            bytes = payload(notification, content, body, showWhenVisible);
        }
        return bytes;
    }

    private byte[] payload(Notification notification, NotificationContent content, String body, boolean showWhenVisible) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", notification.getId());
        payload.put("type", notification.getType().name());
        payload.put("title", content.title());
        payload.put("body", body);
        payload.put("link", content.link());
        payload.put("tag", "notification-" + notification.getId());
        payload.put("showWhenVisible", showWhenVisible);
        return objectMapper.writeValueAsBytes(payload);
    }

    void send(PushSubscription subscription, byte[] payload) {
        URI endpoint = URI.create(subscription.getEndpoint());
        String audience = endpoint.getScheme() + "://" + endpoint.getHost() + (endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort());
        long expiresAt = clock.instant().plus(TOKEN_VALIDITY).getEpochSecond();
        String token = WebPushCrypto.vapidToken(audience, properties.subject(), expiresAt, vapidKeys.privateKey());
        byte[] body = WebPushCrypto.encrypt(payload,
                WebPushCrypto.fromBase64Url(subscription.getP256dh()), WebPushCrypto.fromBase64Url(subscription.getAuth()));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "vapid t=" + token + ", k=" + vapidKeys.publicKey())
                .header("TTL", String.valueOf(properties.ttl().toSeconds()))
                .header("Urgency", "high")
                .header("Content-Type", "application/octet-stream")
                .header("Content-Encoding", "aes128gcm")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null) {
                log.warn("웹 푸시 요청 실패 subscriptionId={}", subscription.getId(), error);
            } else if (response.statusCode() == 404 || response.statusCode() == 410) {
                repository.deleteExpired(subscription.getEndpointHash());
            } else if (response.statusCode() >= 400) {
                log.warn("웹 푸시 거절 subscriptionId={}, status={}, body={}", subscription.getId(), response.statusCode(), response.body());
            }
        });
    }
}
