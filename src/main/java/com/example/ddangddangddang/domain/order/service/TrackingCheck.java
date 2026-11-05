package com.example.ddangddangddang.domain.order.service;

/** 자동 구매 확정 전에 송장을 조회한 결과 (OrderTrackingService.checkForAutoConfirm) */
public enum TrackingCheck {
    DELIVERED,    // 배송 완료
    IN_TRANSIT,   // 조회되지만 아직 배송 완료가 아님
    NOT_FOUND,    // 택배사에 송장 정보가 없음 (잘못되거나 가짜인 송장일 수 있음)
    UNAVAILABLE,  // 조회 서비스 장애·미설정 (확인하지 못함)
    UNSUPPORTED   // 조회할 수 없는 택배사 (기타)
}
