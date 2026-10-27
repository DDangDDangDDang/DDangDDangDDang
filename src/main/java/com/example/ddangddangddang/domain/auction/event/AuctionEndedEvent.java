package com.example.ddangddangddang.domain.auction.event;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;

public record AuctionEndedEvent(
        Long auctionId,
        AuctionStatus result, //ENDED(종료) OR FAILED(취소)
        Long sellerId,
        Long winnerId, //낙찰자(최고 입찰자)
        Long finalPrice, //낙찰가(마감 시점 현재가)
        String productName
) {
    //경매 엔티티에서 바로 이벤트 만들기
    public static AuctionEndedEvent from(Auction auction) {
        boolean sold = auction.getStatus() == AuctionStatus.ENDED;
        return new AuctionEndedEvent(
                auction.getId(),
                auction.getStatus(),
                auction.getProduct().getSeller().getId(),
                sold ? auction.getHighestBidderId() : null,
                sold ? auction.getCurrentPrice() : null,
                auction.getProduct().getName()
        );
    }
}
