package com.example.ddangddangddang.domain.auction.dto.request;

import jakarta.validation.constraints.*;

public record LiveAuctionCreateRequest(
        @NotNull Long productId,
        @NotNull @Positive Long startPrice,
        @NotNull @Positive Long bidUnit,
        @Positive Long buyNowPrice,
        @NotNull @Min(30) @Max(600) Integer durationSec
) {
}
