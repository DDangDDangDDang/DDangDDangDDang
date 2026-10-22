package com.example.ddangddangddang.domain.auction.dto.response;

public record BuyNowResponse(
        Long auctionId,
        Long orderId,
        long price
) {
}
