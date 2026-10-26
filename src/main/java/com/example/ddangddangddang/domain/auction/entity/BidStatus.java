package com.example.ddangddangddang.domain.auction.entity;

/**
 * 입찰 한 건의 상태.
 * ACTIVE: 지금 최고 입찰 (포인트 홀딩 중)
 * OUTBID: 더 높은 입찰이나 즉시구매로 밀려남 (홀딩 해제됨)
 * WON: 마감 때 최고 입찰이라 낙찰됨 (홀딩 포인트 차감됨)
 */
public enum BidStatus {
    ACTIVE,
    OUTBID,
    WON
}
