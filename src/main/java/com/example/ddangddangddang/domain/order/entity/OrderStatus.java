package com.example.ddangddangddang.domain.order.entity;

public enum OrderStatus {
    ESCROW,     // 낙찰 직후(구매자 포인트는 차감, 판매자에게는 대기)
    SHIPPING,   // 판매자가 발송
    COMPLETED   // 구매자가 구매 확정
}
