package com.example.ddangddangddang.domain.order.dto;

import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.infra.TrackingEvent;
import com.example.ddangddangddang.domain.order.infra.TrackingInfo;
import java.util.List;

/**
 * 주문의 배송 조회 결과.
 *
 * @param lastEvent 현재 상태 (배달 완료가 있으면 배달 완료). 이벤트가 없으면 null
 * @param events    이벤트 목록 (오래된 순 → 최신 순, 최근 20개)
 */
public record TrackingResponse(Courier courier, String trackingNo, TrackingEvent lastEvent, List<TrackingEvent> events) {

    public static TrackingResponse of(Courier courier, String trackingNo, TrackingInfo info) {
        return new TrackingResponse(courier, trackingNo, info.lastEvent(), info.events());
    }
}