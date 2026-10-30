package com.example.ddangddangddang.domain.auction.dto.response;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.product.entity.Product;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

public record AuctionResponse(
        Long id,
        Long productId,
        String productName,
        Long sellerId,
        String sellerName,
        Long liveStreamId,
        // 올라간 방송이 지금 방송 중인지. 방송이 끝나면 경매는 그대로 진행돼도 false
        boolean live,
        Long startPrice,
        Long bidUnit,
        Long buyNowPrice,
        Long currentPrice,
        int bidCount,
        AuctionStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime canceledAt,
        // 상품 대표 이미지 주소. 이미지가 없으면 null (방송 화면 경매 정보·스튜디오에 보여 준다)
        String thumbnailUrl
) {

    /** @param imageUrl 저장소 키를 화면에서 보여 줄 주소로 바꾼다 (FileStorage::publicUrl) */
    public static AuctionResponse from(Auction auction, Function<String, String> imageUrl){
        Product product = auction.getProduct();
        return new AuctionResponse(
                auction.getId(),
                product.getId(),
                product.getName(),
                product.getSeller().getId(),
                product.getSeller().getNickname(),
                auction.getLiveStream() != null ? auction.getLiveStream().getId() : null,
                auction.getLiveStream() != null && auction.getLiveStream().getStatus() == LiveStreamStatus.LIVE,
                auction.getStartPrice(),
                auction.getBidUnit(),
                auction.getBuyNowPrice(),
                auction.getCurrentPrice(),
                auction.getBidCount(),
                auction.getStatus(),
                auction.getStartAt(),
                auction.getEndAt(),
                auction.getCanceledAt(),
                product.getThumbnailKey() == null ? null : imageUrl.apply(product.getThumbnailKey())
        );
    }
}
