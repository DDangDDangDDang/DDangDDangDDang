package com.example.ddangddangddang.domain.auction.event;

import com.example.ddangddangddang.domain.auction.entity.Auction;

import java.time.LocalDateTime;

/**
 * 방송에 올라간 경매가 바뀌어 커밋된 뒤, 시청 화면의 경매 목록과 채팅 안내에 반영하기 위한 이벤트.
 * 경매는 방송과 별개라, 방송은 이 이벤트로 경매 정보를 보여주기만 한다.
 */
public record LiveAuctionEvent(
        Long liveStreamId,
        Long auctionId,
        Type type,
        String productName,
        Long currentPrice,
        LocalDateTime startAt
) {

    public enum Type {
        STARTED,  // 방송 중 경매를 열었거나, 방송에 올린 경매가 시작 시각이 되어 시작됨 (채팅 안내)
        SHOWN,    // 이미 만든 경매를 방송에 올림 (채팅 안내)
        CHANGED   // 취소·마감 등 그 밖의 변경 (목록만 갱신)
    }

    public static LiveAuctionEvent of(Auction auction, Type type) {
        return new LiveAuctionEvent(
                auction.getLiveStream().getId(),
                auction.getId(),
                type,
                auction.getProduct().getName(),
                auction.getCurrentPrice(),
                auction.getStartAt()
        );
    }
}
