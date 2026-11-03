package com.example.ddangddangddang.domain.order.event;

import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;

/**
 * 기한 초과로 주문이 취소됐음을 알린다. (상품 복귀, 메신저 안내, 알림)
 * 주문 도메인은 누가 듣는지 모른다.
 */
public record OrderCanceledEvent(Long orderId, Long auctionId, Long buyerId, Long sellerId,
                                 String productName, OrderCancelReason reason,
                                 long refundAmount, long penaltyAmount) {
}
