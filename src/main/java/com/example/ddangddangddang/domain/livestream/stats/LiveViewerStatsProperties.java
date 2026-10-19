package com.example.ddangddangddang.domain.livestream.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 홈 화면 시청자 수 집계 설정. 모든 값에 기본값이 있어 application.yml에 적지 않아도 동작한다.
 *
 * <pre>
 * live-stream.viewer-stats.enabled       집계 사용 여부. 기본 true (테스트 설정에서는 false)
 * live-stream.viewer-stats.interval      LiveKit에서 시청자 수를 가져오는 주기. 기본 15초
 * live-stream.viewer-stats.surge-window  급증 판단 구간. 지금 시청자 수를 이 시간 전과 비교한다. 기본 5분
 * </pre>
 */
@ConfigurationProperties(prefix = "live-stream.viewer-stats")
public record LiveViewerStatsProperties(Boolean enabled, Duration interval, Duration surgeWindow) {

    public LiveViewerStatsProperties {
        enabled = enabled == null || enabled;
        interval = interval == null ? Duration.ofSeconds(15) : interval;
        surgeWindow = surgeWindow == null ? Duration.ofMinutes(5) : surgeWindow;
    }
}
