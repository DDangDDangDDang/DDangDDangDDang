package com.example.ddangddangddang.domain.point.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param storeId 결제창에 쓰는 상점 아이디(store-...)와 같은 값.
 *                조회·취소 API에 함께 보낸다. 비우면 포트원이 계정의 "대표 상점"에서 찾는데,
 *                결제창이 다른 상점(테스트 상점 등)으로 결제했다면 결제를 못 찾아 404가 난다.
 */
@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String apiUrl,
        String apiSecret,
        String webhookSecret,
        String storeId,
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
        if (storeId != null && storeId.isBlank()) {
            storeId = null;
        }
    }
}
