package com.example.ddangddangddang.domain.notification.entity;

public enum NotificationType {
    LIVE_STARTED,          // 구독한 판매자가 방송을 시작함
    AUCTION_STARTED,       // 알림 받기를 한 경매가 시작됨
    AUCTION_OUTBID,        // 내 최고 입찰보다 높은 입찰이 들어옴
    AUCTION_CLOSING_SOON,  // 알림 받기·입찰한 경매가 곧 마감됨 (10분 전)
    AUCTION_CANCELED,      // 알림 받기·입찰한 경매가 취소됨
    AUCTION_WON,           // 내가 낙찰받음 (입찰 마감·즉시 구매)
    AUCTION_SOLD,          // (판매자) 내 경매가 낙찰됨
    AUCTION_FAILED,        // (판매자) 내 경매가 입찰 없이 끝남 (유찰)
    AUCTION_ENDED          // 알림 받기·입찰한 경매가 다른 사람에게 낙찰되거나 유찰로 끝남
}
