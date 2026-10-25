package com.example.ddangddangddang.domain.auction.event;

/**
 * 시작 전 경매가 시작 시각이 되어 시작됨. 커밋된 뒤 "경매 알림 받기"를 한 회원에게 알린다. (notification 도메인)
 */
public record AuctionStartedEvent(Long auctionId) {
}
