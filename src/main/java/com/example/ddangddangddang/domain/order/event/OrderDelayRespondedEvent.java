package com.example.ddangddangddang.domain.order.event;

/** 구매자가 발송 지연 신청에 응답했음을 알린다. (메신저 안내) */
public record OrderDelayRespondedEvent(Long orderId, Long auctionId, Long buyerId, Long sellerId,
                                       String productName, boolean approved) {
}
