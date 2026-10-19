package com.example.ddangddangddang.domain.point.infra;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 포트원 웹훅 서명 검증 (Standard Webhooks 규격).
 *
 * 서명 = Base64( HMAC-SHA256( 시크릿, "{webhook-id}.{webhook-timestamp}.{원본 body}" ) )
 * webhook-signature 헤더는 "v1,서명" 형태이고, 시크릿 교체 기간에는 공백으로 구분해 여러 개가 올 수 있다.
 *
 * 주의: body는 JSON으로 파싱하기 전의 원본 문자열이어야 한다. 공백 하나만 달라도 서명이 달라진다.
 */
@Component
public class PortOneWebhookVerifier {

    /** 이보다 오래된 웹훅은 재전송 공격으로 보고 거부한다 */
    private static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final String SECRET_PREFIX = "whsec_";
    private static final String SIGNATURE_VERSION = "v1,";

    private final byte[] secretKey;
    private final Clock clock;

    @Autowired
    public PortOneWebhookVerifier(PortOneProperties properties) {
        this(properties.webhookSecret(), Clock.systemUTC());
    }

    /** 테스트에서 시각을 고정하기 위한 생성자 */
    PortOneWebhookVerifier(String webhookSecret, Clock clock) {
        this.secretKey = decodeSecret(webhookSecret);
        this.clock = clock;
    }

    public void verify(String body, String webhookId, String webhookTimestamp, String webhookSignature) {
        if (secretKey.length == 0) {
            // 시크릿이 설정되지 않았으면 모든 웹훅을 거부한다 (검증 없이 통과시키지 않는다)
            throw invalid();
        }
        if (isBlank(body) || isBlank(webhookId) || isBlank(webhookTimestamp) || isBlank(webhookSignature)) {
            throw invalid();
        }

        verifyTimestamp(webhookTimestamp);

        byte[] expected = sign(webhookId + "." + webhookTimestamp + "." + body);
        for (String candidate : webhookSignature.split(" ")) {
            if (!candidate.startsWith(SIGNATURE_VERSION)) {
                continue;
            }
            byte[] actual = decodeBase64(candidate.substring(SIGNATURE_VERSION.length()));
            // 문자열 비교(equals) 대신 상수 시간 비교를 써서 타이밍 공격을 막는다
            if (actual != null && MessageDigest.isEqual(expected, actual)) {
                return;
            }
        }
        throw invalid();
    }

    private void verifyTimestamp(String webhookTimestamp) {
        long seconds;
        try {
            seconds = Long.parseLong(webhookTimestamp);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        Instant sentAt = Instant.ofEpochSecond(seconds);
        Instant now = clock.instant();
        if (sentAt.isBefore(now.minus(TOLERANCE)) || sentAt.isAfter(now.plus(TOLERANCE))) {
            throw invalid();
        }
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("웹훅 서명 계산 실패", e);
        }
    }

    /** "whsec_" 접두사를 떼고 Base64 디코딩한다. 디코딩이 안 되면 문자열 그대로를 키로 쓴다 */
    private static byte[] decodeSecret(String secret) {
        if (isBlank(secret)) {
            return new byte[0];
        }
        String value = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
        byte[] decoded = decodeBase64(value);
        return decoded != null ? decoded : value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BusinessException invalid() {
        return new BusinessException(PointErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }
}
