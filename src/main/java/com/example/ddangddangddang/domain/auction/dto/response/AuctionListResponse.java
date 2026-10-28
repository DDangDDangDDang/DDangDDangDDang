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
        boolean promoted //경매 위로 올리기(마일리지 상점)로 지금 올라가 있는지. 목록에 '홍보'로 표시
) {
}
