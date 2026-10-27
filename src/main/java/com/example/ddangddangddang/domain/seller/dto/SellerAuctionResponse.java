package com.example.ddangddangddang.domain.seller.dto;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;

import java.time.LocalDateTime;

/**
 * 판매자 프로필 경매 기록 한 줄. 판매자가 진행한 경매(취소 포함)를 최근 순으로 보여 준다.
 * currentPrice: 진행 중이면 현재가, 낙찰이면 낙찰가
 * liveStreamId: 방송에서 연 경매면 그 방송 (방송이 끝났을 수 있음)
 */
public record SellerAuctionResponse(
        Long auctionId,
        String productName,
        AuctionStatus status,
        Long startPrice,
        Long currentPrice,
        int bidCount,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long liveStreamId
) {
    public static SellerAuctionResponse from(Auction auction) {
        return new SellerAuctionResponse(auction.getId(), auction.getProduct().getName(), auction.getStatus(),
                auction.getStartPrice(), auction.getCurrentPrice(), auction.getBidCount(), auction.getStartAt(),
                auction.getEndAt(), auction.getLiveStream() == null ? null : auction.getLiveStream().getId());
    }
}
