package com.example.ddangddangddang.domain.order.entity;

public enum OrderCancelReason {
    BUYER_NO_ADDRESS,   // 구매자가 기한 안에 배송지를 입력하지 않음 (낙찰취소)
    SELLER_NOT_SHIPPED  // 판매자가 기한 안에 발송하지 않음 (제품 미발송)
}
