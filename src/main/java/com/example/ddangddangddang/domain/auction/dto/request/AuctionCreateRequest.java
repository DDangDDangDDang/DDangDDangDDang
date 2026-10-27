package com.example.ddangddangddang.domain.auction.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record AuctionCreateRequest(
        @NotNull Long productId,
        @NotNull @Positive Long startPrice,
        @NotNull @Positive Long bidUnit,
        @Positive Long buyNowPrice,
        @NotNull @Future LocalDateTime startAt,
        @NotNull @Future LocalDateTime endAt
        ) {

}
