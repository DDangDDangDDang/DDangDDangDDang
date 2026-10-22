package com.example.ddangddangddang.domain.auction.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BidRequest(
        @NotNull(message = "입찰 금액이 없습니다.")
        @Positive(message = "입찰 금액은 0보다 커야 합니다.")
        Long amount
) {
}
