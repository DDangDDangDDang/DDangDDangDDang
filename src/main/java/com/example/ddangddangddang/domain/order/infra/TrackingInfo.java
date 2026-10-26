package com.example.ddangddangddang.domain.order.infra;

import java.util.List;

/**
 * 송장 하나의 배송 조회 결과.
 *
 * @param lastEvent 현재 상태를 가장 잘 나타내는 이벤트. 배달 완료가 있으면 그 뒤에 다른 이벤트가 와도 배달 완료다
 * @param events    이벤트 목록 (오래된 순 → 최신 순)
 */
public record TrackingInfo(TrackingEvent lastEvent, List<TrackingEvent> events) {

    public boolean isDelivered() {
        return lastEvent != null && lastEvent.isDelivered();
    }
}