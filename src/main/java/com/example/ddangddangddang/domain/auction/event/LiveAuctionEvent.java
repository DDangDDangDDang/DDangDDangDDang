package com.example.ddangddangddang.domain.auction.event;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;

import java.time.LocalDateTime;

/**
 * 방송에 올라간 경매가 바뀌어 커밋된 뒤, 시청 화면의 경매 목록과 채팅 안내에 반영하기 위한 이벤트.
 * 경매는 방송과 별개라, 방송은 이 이벤트로 경매 정보를 보여주기만 한다.
 * userId: BID면 입찰한 회원, SOLD면 낙찰받은 회원 (그 밖에는 null)
 * previousPrice: BID에서 이 입찰 직전의 현재가 (첫 입찰이면 시작가, 그 밖에는 null). 화면이 오른 비율로 이펙트 크기를 정한다.
 */
public record LiveAuctionEvent(
        Long liveStreamId,
        Long auctionId,
        Type type,
        String productName,
        Long currentPrice,
        LocalDateTime startAt,
        Long userId,
        Long previousPrice
) {

    public enum Type {
        STARTED,  // 방송 중 경매를 열었거나, 방송에 올린 경매가 시작 시각이 되어 시작됨 (채팅 안내)
        SHOWN,    // 이미 만든 경매를 방송에 올림 (채팅 안내)
        BID,      // 누군가 입찰함 (방송 화면 입찰 이펙트 + 채팅)
        SOLD,     // 낙찰됨: 마감 때 최고 입찰자가 있었거나 즉시 구매 (방송 화면 낙찰 이펙트 + 채팅)
        CHANGED   // 취소·유찰 등 그 밖의 변경 (목록만 갱신)
    }

    public static LiveAuctionEvent of(Auction auction, Type type) {
        return new LiveAuctionEvent(
                auction.getLiveStream().getId(),
                auction.getId(),
                type,
                auction.getProduct().getName(),
                auction.getCurrentPrice(),
                auction.getStartAt(),
                null,
                null
        );
    }

    public static LiveAuctionEvent bid(Long liveStreamId, Long auctionId, String productName, long amount, LocalDateTime startAt,
                                       Long bidderId, long previousPrice) {
        return new LiveAuctionEvent(liveStreamId, auctionId, Type.BID, productName, amount, startAt, bidderId, previousPrice);
    }

    /** 마감·즉시 구매 뒤의 경매로 만든다. 낙찰자가 있으면 SOLD, 없으면(유찰·취소) CHANGED */
    public static LiveAuctionEvent closed(Auction auction) {
        if (auction.getStatus() != AuctionStatus.ENDED || auction.getHighestBidderId() == null) {
            return of(auction, Type.CHANGED);
        }
        return new LiveAuctionEvent(
                auction.getLiveStream().getId(),
                auction.getId(),
                Type.SOLD,
                auction.getProduct().getName(),
                auction.getCurrentPrice(),
                auction.getStartAt(),
                auction.getHighestBidderId(),
                null
        );
    }
}
