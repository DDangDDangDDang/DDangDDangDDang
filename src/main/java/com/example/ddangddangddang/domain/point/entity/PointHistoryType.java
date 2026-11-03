package com.example.ddangddangddang.domain.point.entity;

public enum PointHistoryType {
    CHARGE,     // 충전: 사용 가능
    HOLD,       // 입찰: 사용 가능 -> 홀딩
    RELEASE,    // 상위 입찰 발생: 홀딩 -> 사용 가능
    PURCHASE,   // 낙찰 or 즉시구매
    SETTLE,     // 구매 확정
    EXCHANGE,
    EXCHANGE_REFUND,
    ORDER_REFUND,   // 판매자 미발송으로 주문 취소: 낙찰 금액을 구매자에게 돌려줌
    ORDER_PENALTY,  // 구매자 배송지 미입력으로 취소: 판매자가 위약금을 받음
    WIN_CANCEL,     // 구매자 배송지 미입력으로 낙찰취소: 낙찰 금액을 구매자에게 돌려줌
    WIN_CANCEL_PENALTY  // 구매자 배송지 미입력으로 낙찰취소: 구매자가 위약금을 냄
}
