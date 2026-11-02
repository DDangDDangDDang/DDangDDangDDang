package com.example.ddangddangddang.domain.shop.dto;

import com.example.ddangddangddang.domain.shop.entity.AuctionBoost;

import java.time.LocalDateTime;

/** 위로 올린 경매. endsAt까지 경매 목록(최신순) 맨 위에 "홍보"로 보인다. */
public record AuctionBoostResponse(Long auctionId, LocalDateTime startsAt, LocalDateTime endsAt) {

    public static AuctionBoostResponse from(AuctionBoost boost) {
        return new AuctionBoostResponse(boost.getAuctionId(), boost.getStartsAt(), boost.getEndsAt());
    }
}
