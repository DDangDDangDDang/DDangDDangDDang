package com.example.ddangddangddang.domain.auction.dto.response;

import com.example.ddangddangddang.domain.auction.entity.Bid;

import java.time.LocalDateTime;

public record BidHistoryResponse(
        Long bidId,
        Long bidderId,
        long amount,
        LocalDateTime createdAt
) {

    public static BidHistoryResponse from(Bid bid) {
        return new BidHistoryResponse(bid.getId(), bid.getBidderId(), bid.getAmount(), bid.getCreatedAt());
    }
}
