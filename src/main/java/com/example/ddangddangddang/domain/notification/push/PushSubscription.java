package com.example.ddangddangddang.domain.notification.push;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 브라우저 알림(웹 푸시) 구독. 브라우저·기기마다 하나씩 생긴다. (한 회원이 PC 두 대에서 켜면 두 개)
 * 주소(endpoint)는 길어서(수백 자) 유니크 인덱스는 SHA-256 값에 건다.
 * 같은 브라우저에서 다른 회원이 로그인해 구독하면 그 회원의 구독으로 바뀐다.
 */
@Entity
@Table(
        name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_subscription_endpoint", columnNames = "endpoint_hash"),
        // 알림을 받을 회원들의 구독 조회
        indexes = @Index(name = "idx_push_subscription_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 푸시 서비스 주소 (https://fcm.googleapis.com/fcm/send/... 등)
    @Column(nullable = false, length = 1000)
    private String endpoint;

    @Column(name = "endpoint_hash", nullable = false, length = 64)
    private String endpointHash;

    // 브라우저 공개키(base64url, 65바이트)와 인증 비밀값(base64url, 16바이트). 본문 암호화에 쓴다
    @Column(nullable = false, length = 100)
    private String p256dh;

    @Column(nullable = false, length = 50)
    private String auth;

    private PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.endpointHash = hash(endpoint);
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public static PushSubscription create(Long userId, String endpoint, String p256dh, String auth) {
        return new PushSubscription(userId, endpoint, p256dh, auth);
    }

    /** 같은 브라우저에서 다시 구독함 (다른 회원이 로그인했거나 키가 바뀜) */
    public void renew(Long userId, String p256dh, String auth) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
