package com.example.ddangddangddang.domain.auction.dto.response;

import java.time.LocalDateTime;

/** 입찰 성공 결과. 연장됐다면 extended=true, endAt은 연장된 종료 시각 */
public record BidResponse(
        Long bidId,
        Long auctionId,
        long amount,
        int bidCount,
        LocalDateTime endAt,
        boolean extended
) {
}
