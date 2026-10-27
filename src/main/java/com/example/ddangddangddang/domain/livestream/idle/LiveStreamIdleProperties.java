package com.example.ddangddangddang.domain.livestream.idle;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 송출이 멈춘 방송 자동 종료 설정. 모든 값에 기본값이 있어 application.yml에 적지 않아도 동작한다.
 *
 * <pre>
 * live-stream.idle.enabled     감시 사용 여부. 기본 true (테스트 설정에서는 false)
 * live-stream.idle.interval    LiveKit에서 판매자 송출을 확인하는 주기. 기본 1분
 * live-stream.idle.warn-after  송출이 이만큼 없으면 판매자에게 곧 종료된다고 알린다. 기본 25분
 * live-stream.idle.end-after   송출이 이만큼 없으면 방송을 종료한다. 기본 30분
 * </pre>
 */
@ConfigurationProperties(prefix = "live-stream.idle")
public record LiveStreamIdleProperties(Boolean enabled, Duration interval, Duration warnAfter, Duration endAfter) {

    public LiveStreamIdleProperties {
        enabled = enabled == null || enabled;
        interval = interval == null ? Duration.ofMinutes(1) : interval;
        warnAfter = warnAfter == null ? Duration.ofMinutes(25) : warnAfter;
        endAfter = endAfter == null ? Duration.ofMinutes(30) : endAfter;
    }
}
