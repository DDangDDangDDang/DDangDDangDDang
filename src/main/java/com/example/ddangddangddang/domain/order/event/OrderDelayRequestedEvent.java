package com.example.ddangddangddang.domain.order.event;

/** 판매자가 발송 지연을 신청했음을 알린다. (메신저 안내) */
public record OrderDelayRequestedEvent(Long orderId, Long auctionId, Long buyerId, Long sellerId,
                                       String productName, String reason) {
}
