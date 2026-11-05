package com.example.ddangddangddang.domain.order.event;

import com.example.ddangddangddang.domain.order.entity.OrderIssueType;
import com.example.ddangddangddang.domain.order.entity.OrderReviewReason;

/** 주문이 관리자 확인 대기로 바뀌었다. (구매자 신고, 송장 확인 실패) 자동 구매 확정이 멈췄다고 양쪽에 알린다. */
public record OrderReviewRequestedEvent(Long orderId, Long auctionId, Long buyerId, Long sellerId, String productName,
                                        OrderReviewReason reason, OrderIssueType issueType) {
}
