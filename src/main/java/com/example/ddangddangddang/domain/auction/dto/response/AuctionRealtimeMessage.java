package com.example.ddangddangddang.domain.auction.dto.response;

import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.event.AuctionEndedEvent;
import com.example.ddangddangddang.domain.auction.event.BidPlacedEvent;

import java.time.LocalDateTime;

/**
 * 경매 구독자(/topic/auctions/{auctionId})에게 보내는 메시지. 손님도 받으므로 회원 번호는 넣지 않는다.
 * 내가 최고 입찰자인지는 GET /api/v1/auctions/{auctionId}/bids/me 로 확인한다.
 */
public final class AuctionRealtimeMessage {

    private AuctionRealtimeMessage() {
    }

    /** 입찰이 반영됨. bidderNickname은 가린 닉네임 */
    public record BidPlaced(
            Long auctionId,
            String bidderNickname,
            long amount,
            int bidCount,
            LocalDateTime endAt,
            boolean extended
    ) {
        public static BidPlaced of(BidPlacedEvent event, String maskedNickname) {
            return new BidPlaced(event.auctionId(), maskedNickname, event.amount(), event.bidCount(), event.endAt(), event.extended());
        }
    }

    /** 경매가 끝남. result: ENDED(낙찰·즉시구매) 또는 FAILED(유찰) */
    public record Ended(
            Long auctionId,
            AuctionStatus result,
            Long finalPrice,
            String productName
    ) {
        public static Ended of(AuctionEndedEvent event) {
            return new Ended(event.auctionId(), event.result(), event.finalPrice(), event.productName());
        }
    }
}
