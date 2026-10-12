package com.example.ddangddangddang.domain.point.entity;

public enum PointHistoryType {
    CHARGE,     // 충전: 사용 가능
    HOLD,       // 입찰: 사용 가능 -> 홀딩
    RELEASE,    // 상위 입찰 발생: 홀딩 -> 사용 가능
    PURCHASE,   // 낙찰 or 즉시구매
    SETTLE      // 구매 확정
}
