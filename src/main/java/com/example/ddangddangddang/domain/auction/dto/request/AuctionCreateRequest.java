package com.example.ddangddangddang.domain.auction.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record AuctionCreateRequest(
        @NotNull Long productId,
        // 시작가·최소 입찰 단위: 둘 다 비우면 입찰 없이 즉시구매로만 파는 경매 (즉시구매가 필수)
        @Positive Long startPrice,
        @Positive Long bidUnit,
        @Positive Long buyNowPrice,
        @NotNull @Future LocalDateTime startAt,
        @NotNull @Future LocalDateTime endAt
        ) {

}
