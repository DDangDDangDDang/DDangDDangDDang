package com.example.ddangddangddang.domain.point.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String apiUrl,
        String apiSecret,
        String webhookSecret,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    /** yml에 타임아웃이 없으면 기본값을 쓴다. 타임아웃이 없으면 포트원 장애 시 요청이 무한정 대기한다 */
    public PortOneProperties {
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
    }
}
