package com.example.ddangddangddang.domain.auction.event;

/**
 * 더 높은 입찰이 들어와 최고 입찰자가 바뀜. 커밋된 뒤 밀려난 회원(outbidUserId)에게 알린다. (notification 도메인)
 * 경매 화면 갱신용 BidPlacedEvent와 따로 둔다. (그 이벤트는 경매 구독 화면에 그대로 방송되므로 이전 입찰자를 싣지 않는다)
 */
public record BidOutbidEvent(Long auctionId, Long outbidUserId, long newAmount) {
}
