package com.example.ddangddangddang.domain.product.dto.response;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.service.ProductImageResolver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 상품.
 * thumbnailUrl: 대표 이미지(images의 첫 장) 주소. 이미지가 없으면 null
 * images: 상품 이미지(순서대로)
 * descriptionImages: 설명 속 "![](키)" 줄에 들어갈 이미지. 화면은 키로 찾아 그 자리에 그린다.
 */
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
        AuctionStatus auctionStatus,
        String thumbnailUrl,
        List<ProductImageResponse> images,
        List<ProductImageResponse> descriptionImages
) {
    //경매 정보 필요없는 상품 기능 (상세,등록,수정 사용)
    public static ProductResponse from(Product product, Function<String, String> imageUrl){
        return from(product, null, imageUrl);
    }

    /** @param imageUrl 저장소 키를 화면에서 보여 줄 주소로 바꾼다 (FileStorage::publicUrl) */
    public static ProductResponse from(Product product, Auction auction, Function<String, String> imageUrl){
        Function<String, ProductImageResponse> toImage = key -> new ProductImageResponse(key, imageUrl.apply(key));
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
                auction == null ? null : auction.getStatus(),
                product.getThumbnailKey() == null ? null : imageUrl.apply(product.getThumbnailKey()),
                product.getImageKeys().stream().map(toImage).toList(),
                ProductImageResolver.descriptionImageKeys(product.getDescription()).stream().map(toImage).toList()
        );
    }

}
