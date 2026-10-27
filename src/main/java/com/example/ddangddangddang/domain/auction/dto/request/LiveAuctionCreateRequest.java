package com.example.ddangddangddang.domain.auction.dto.request;

import jakarta.validation.constraints.*;

public record LiveAuctionCreateRequest(
        @NotNull Long productId,
        @NotNull @Positive Long startPrice,
        @NotNull @Positive Long bidUnit,
        @Positive Long buyNowPrice,
        // 진행 시간(초): 30초 ~ 3일. 방송이 끝나도 경매는 정한 시간까지 이어진다.
        @NotNull
        @Min(value = MIN_DURATION_SEC, message = "진행 시간은 30초 이상으로 정해 주세요.")
        @Max(value = MAX_DURATION_SEC, message = "진행 시간은 3일 이내로 정해 주세요.")
        Integer durationSec
) {
    public static final int MIN_DURATION_SEC = 30;
    public static final int MAX_DURATION_SEC = 3 * 24 * 60 * 60;
}
