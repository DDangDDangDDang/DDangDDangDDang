package com.example.ddangddangddang.domain.order.event;

/** 관리자가 확인 대기 주문을 처리했다. refunded면 구매자 환불, 아니면 판매자 정산 */
public record OrderReviewResolvedEvent(Long orderId, Long auctionId, Long buyerId, Long sellerId, String productName,
                                       boolean refunded, long amount) {
}
