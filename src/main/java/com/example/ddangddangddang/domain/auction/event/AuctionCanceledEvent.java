package com.example.ddangddangddang.domain.auction.event;

/**
 * 판매자가 경매를 취소함. 커밋된 뒤 알림을 받기로 한 회원에게 알린다. (notification 도메인)
 */
public record AuctionCanceledEvent(Long auctionId) {
}
