package com.example.ddangddangddang.domain.order.infra;

/**
 * 배송 조회 이벤트 하나 (예: 집화처리, 간선상차, 배달완료).
 *
 * @param time        발생 시각 (ISO-8601, 오프셋 포함. 예: 2026-10-26T14:03:00+09:00)
 * @param statusCode  통합 상태 코드: INFORMATION_RECEIVED, AT_PICKUP, IN_TRANSIT, OUT_FOR_DELIVERY,
 *                    ATTEMPT_FAIL, DELIVERED, AVAILABLE_FOR_PICKUP, EXCEPTION, UNKNOWN
 * @param statusName  택배사가 붙인 상태 이름 (예: 간선상차)
 * @param location    위치 (예: 곤지암Hub). 없으면 null
 * @param description 상세 내용. 없으면 null
 */
public record TrackingEvent(String time, String statusCode, String statusName, String location, String description) {

    public boolean isDelivered() {
        return "DELIVERED".equals(statusCode);
    }
}