package com.example.ddangddangddang.domain.livestream.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 영상 위에 띄울 경매. 이 방송에 올라간 시작 전·진행 중 경매여야 한다.
 */
public record FeaturedAuctionRequest(
        @NotNull(message = "띄울 경매를 골라 주세요.")
        Long auctionId
) {
}
