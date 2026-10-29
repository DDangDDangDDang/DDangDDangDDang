package com.example.ddangddangddang.domain.shop.dto;

import jakarta.validation.constraints.NotNull;

/** 위로 올릴 내 경매 */
public record AuctionBoostRequest(
        @NotNull(message = "경매를 골라 주세요.")
        Long auctionId
) {
}
