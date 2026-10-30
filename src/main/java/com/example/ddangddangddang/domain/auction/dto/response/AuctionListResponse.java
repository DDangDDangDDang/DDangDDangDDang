package com.example.ddangddangddang.domain.auction.dto.response;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.product.entity.Product;

import java.time.LocalDateTime;
import java.util.function.Function;

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
        boolean promoted, //경매 위로 올리기(마일리지 상점)로 지금 올라가 있는지. 목록에 '홍보'로 표시
        String thumbnailUrl //상품 대표 이미지 주소. 이미지가 없으면 null
) {
    /**
     * 목록 쿼리(AuctionRepositoryImpl)는 대표 이미지 키(products.thumbnail_key)를 thumbnailUrl 자리에 담아 온다.
     * 서비스가 이 메서드로 화면에서 보여 줄 주소로 바꿔 내보낸다.
     */
    public AuctionListResponse withThumbnailKeyResolved(Function<String, String> imageUrl) {
        return new AuctionListResponse(auctionId, productId, productName, categoryName, currentPrice, bidCount, status,
                startAt, endAt, live, promoted, thumbnailUrl == null ? null : imageUrl.apply(thumbnailUrl));
    }
}
