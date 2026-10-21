package com.example.ddangddangddang.domain.auction.dto.response;

/**
 * 방송에 올라간 경매가 바뀌었다는 알림 (/topic/live-streams/{id}/auctions). 바뀐 경매 번호만 담는다.
 * 받은 쪽은 방송의 경매 목록을 다시 불러온다.
 */
public record LiveAuctionChangedMessage(String type, Long auctionId) {

    public static LiveAuctionChangedMessage of(Long auctionId) {
        return new LiveAuctionChangedMessage("CHANGED", auctionId);
    }
}
