package com.example.ddangddangddang.domain.auction.dto.request;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BidRequest(
        @NotNull(message = "입찰 금액이 없습니다.")
        @Positive(message = "입찰 금액은 0보다 커야 합니다.")
        @Max(value = Auction.MAX_PRICE, message = "입찰 금액은 1억 원까지 넣을 수 있습니다.")
        Long amount
) {
}
