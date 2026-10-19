package com.example.ddangddangddang.domain.product.dto.response;

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
        LocalDateTime createdAt
) {
    public static ProductResponse from(Product product){
        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getNickname(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getDescription(),
                product.getStatus(),
                product.getCreatedAt()
        );
    }

}
