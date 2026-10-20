package com.example.ddangddangddang.domain.point.infra;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Standard Webhooks 규격의 공개 테스트 벡터로 검증한다.
 * 같은 코드로 서명을 만들어 비교하는 방식이 아니라, 규격에서 정답으로 제시한 값과 맞는지 확인한다.
 */
class PortOneWebhookVerifierTest {

    private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
    private static final String ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
    private static final String TIMESTAMP = "1614265330";
    private static final String BODY = "{\"test\": 2432232314}";
    private static final String SIGNATURE = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";

    /** 웹훅이 보내진 시각에 고정한 시계 */
    private static final Clock AT_SEND_TIME =
            Clock.fixed(Instant.ofEpochSecond(Long.parseLong(TIMESTAMP)), ZoneOffset.UTC);

    private final PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(SECRET, AT_SEND_TIME);

    @Test
    @DisplayName("규격 테스트 벡터의 서명은 통과한다")
    void valid() {
        assertThatCode(() -> verifier.verify(BODY, ID, TIMESTAMP, SIGNATURE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("여러 서명 중 하나만 맞아도 통과한다 (시크릿 교체 기간)")
    void multipleSignatures() {
        String signatures = "v1,aW52YWxpZCBzaWduYXR1cmU= " + SIGNATURE;
        assertThatCode(() -> verifier.verify(BODY, ID, TIMESTAMP, signatures)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("body가 한 글자라도 다르면 거부한다")
    void tamperedBody() {
        assertInvalid(() -> verifier.verify("{\"test\":2432232314}", ID, TIMESTAMP, SIGNATURE));
    }

    @Test
    @DisplayName("webhook-id가 다르면 거부한다")
    void tamperedId() {
        assertInvalid(() -> verifier.verify(BODY, "msg_other", TIMESTAMP, SIGNATURE));
    }

    @Test
    @DisplayName("다른 시크릿으로 만든 서명은 거부한다")
    void wrongSecret() {
        PortOneWebhookVerifier other = new PortOneWebhookVerifier("whsec_dGVzdC13ZWJob29rLXNlY3JldA==", AT_SEND_TIME);
        assertInvalid(() -> other.verify(BODY, ID, TIMESTAMP, SIGNATURE));
    }

    @Test
    @DisplayName("5분보다 오래된 웹훅은 서명이 맞아도 거부한다 (재전송 공격 방지)")
    void expired() {
        Clock sixMinutesLater = Clock.fixed(Instant.ofEpochSecond(Long.parseLong(TIMESTAMP) + 360), ZoneOffset.UTC);
        PortOneWebhookVerifier late = new PortOneWebhookVerifier(SECRET, sixMinutesLater);

        assertInvalid(() -> late.verify(BODY, ID, TIMESTAMP, SIGNATURE));
    }

    @Test
    @DisplayName("시크릿이 설정되지 않았으면 모든 웹훅을 거부한다")
    void noSecret() {
        PortOneWebhookVerifier noSecret = new PortOneWebhookVerifier("", AT_SEND_TIME);
        assertInvalid(() -> noSecret.verify(BODY, ID, TIMESTAMP, SIGNATURE));
    }

    @Test
    @DisplayName("v1 형식이 아닌 서명은 무시한다")
    void wrongVersion() {
        assertInvalid(() -> verifier.verify(BODY, ID, TIMESTAMP, "v2,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE="));
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }
}
