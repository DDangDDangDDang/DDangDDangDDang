package com.example.ddangddangddang.domain.notification.dto;

/** 경매 알림 받기 상태. subscribed: 시작·마감 임박·취소·종료 알림을 받기로 함 */
public record AuctionAlertResponse(Long auctionId, boolean subscribed) {
}
