package com.example.ddangddangddang.domain.seller.dto;

/** 구독·구독 취소 결과. 화면은 받은 구독자 수로 바로 바꾼다. */
public record SubscriptionResponse(
        Long sellerId,
        boolean subscribed,
        long subscriberCount
) {
}
