package com.example.ddangddangddang.domain.auction.dto.response;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.product.entity.Product;

import java.time.LocalDateTime;

public record AuctionListResponse(
        Long auctionId,
        Long productId,
        String productName,
        String categoryName,
        Long currentPrice,
        int bidCount,
        AuctionStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean live, //라이브 경매 여부
        boolean buyNowOnly //입찰 없이 즉시구매로만 파는 경매. true면 currentPrice가 즉시구매가
) {
}
