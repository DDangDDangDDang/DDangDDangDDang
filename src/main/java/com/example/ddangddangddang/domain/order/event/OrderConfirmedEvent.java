package com.example.ddangddangddang.domain.order.event;

/**
 * 구매자가 구매 확정한 뒤(커밋 뒤) 다른 도메인이 할 일을 알린다. (도네이션 포인트 적립)
 * 주문 도메인은 누가 듣는지 모른다.
 */
public record OrderConfirmedEvent(Long orderId, Long buyerId, Long sellerId, long finalPrice) {
}
