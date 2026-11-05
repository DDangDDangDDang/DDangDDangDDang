package com.example.ddangddangddang.domain.order.entity;

/**
 * 주문을 관리자가 확인해야 하는 이유. 이 값이 있는 발송된 주문은 자동 구매 확정을 멈춘다. (Order.reviewReason)
 * 컬럼은 MySQL ENUM이 아니라 문자열이라 값을 더해도 운영 DB 스키마를 고치지 않아도 된다.
 */
public enum OrderReviewReason {
    BUYER_REPORTED,         // 구매자가 문제를 신고함 (받지 못함·다른 상품·파손 등)
    TRACKING_NOT_FOUND,     // 발송 후 7일이 지나도 송장이 조회되지 않음 (잘못되거나 가짜인 송장일 수 있음)
    TRACKING_NOT_DELIVERED, // 발송 후 14일이 지나도 배송 완료가 확인되지 않음
    TRACKING_UNAVAILABLE    // 발송 후 14일이 지나도 배송 조회 서비스 장애로 확인하지 못함
}
