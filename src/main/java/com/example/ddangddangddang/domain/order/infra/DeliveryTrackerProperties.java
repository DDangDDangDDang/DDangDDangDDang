package com.example.ddangddangddang.domain.order.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 배송 조회(Delivery Tracker) 설정. 키가 없어도 서버는 뜬다. 키 없이 배송 조회를 호출하면 ORDER_016으로 응답한다.
 *
 * @param clientId     Delivery Tracker 콘솔(console.tracker.delivery)에서 발급한 Client ID
 * @param clientSecret 같은 곳의 Client Secret. Free 플랜은 21일마다 만료되어 다시 발급해야 한다
 */
@ConfigurationProperties(prefix = "app.delivery-tracker")
public record DeliveryTrackerProperties(
        String clientId,
        String clientSecret,
        String apiUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public DeliveryTrackerProperties {
        if (!StringUtils.hasText(apiUrl)) {
            apiUrl = "https://apis.tracker.delivery/graphql";
        }
        // 타임아웃이 없으면 배송 조회 장애 시 요청 스레드가 무한정 대기한다. (문서 권장 최대 15초보다 짧게)
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(8);
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }
}