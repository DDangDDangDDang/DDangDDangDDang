package com.example.ddangddangddang.domain.order.entity;

/** 관리자 확인 결과. (Order.reviewResult) */
public enum OrderReviewResult {
    CONFIRMED, // 판매자에게 정산 (구매 확정)
    REFUNDED   // 구매자에게 전액 환불 (주문 취소)
}
