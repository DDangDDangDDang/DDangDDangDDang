package com.example.ddangddangddang.domain.product.dto.response;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;

import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        Status status,
        LocalDateTime createdAt,
        Long auctionId,
        AuctionStatus auctionStatus
) {
    //경매 정보 필요없는 상품 기능 (상세,등록,수정 사용)
    public static ProductResponse from(Product product){
        return from(product, null);
    }

    public static ProductResponse from(Product product, Auction auction){
        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getNickname(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getDescription(),
                product.getStatus(),
                product.getCreatedAt(),
                auction == null ? null : auction.getId(),
                auction == null ? null : auction.getStatus()
        );
    }

}
