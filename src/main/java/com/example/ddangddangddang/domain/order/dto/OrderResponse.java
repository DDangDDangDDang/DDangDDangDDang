package com.example.ddangddangddang.domain.order.dto;

import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.entity.OrderDelayStatus;
import com.example.ddangddangddang.domain.order.entity.OrderIssueType;
import com.example.ddangddangddang.domain.order.entity.OrderReviewReason;
import com.example.ddangddangddang.domain.order.entity.OrderReviewResult;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        String orderNo,
        Long auctionId,
        Long buyerId,
        Long sellerId,
        String productName,
        String productImageUrl,
        long finalPrice,
        int commissionRateBps,  // 판매 수수료율(bp, 500 = 5%)
        long commission,        // 판매 수수료
        long settlementAmount,  // 판매자가 받을 금액 = finalPrice - commission
        long noAddressPenalty,  // 배송지 미입력 취소 시 판매자에게 줄 위약금. 도입 전 주문은 0
        OrderStatus status,
        String receiverName,
        String receiverPhone,
        String address,
        String deliveryMemo,
        Courier courier,
        String trackingNo,
        LocalDateTime createdAt,
        LocalDateTime shippedAt,
        LocalDateTime completedAt,
        LocalDateTime addressDeadlineAt,  // 배송지 입력 기한. 도입 전 주문은 null
        LocalDateTime shipDeadlineAt,     // 발송 기한. 배송지 입력 전이나 도입 전 주문은 null
        OrderDelayStatus delayStatus,     // 지연 신청 전이면 null
        String delayReason,
        OrderCancelReason cancelReason,
        LocalDateTime canceledAt,
        OrderReviewReason reviewReason,   // 관리자 확인이 필요한 이유. 없으면 null (있고 reviewResult가 없으면 자동 구매 확정이 멈춘 상태)
        OrderIssueType issueType,         // 구매자가 신고한 문제 종류. 구매자 신고가 아니면 null
        String issueDetail,
        LocalDateTime reviewRequestedAt,
        OrderReviewResult reviewResult,   // 관리자 처리 결과(정산·환불). 처리 전이면 null
        LocalDateTime reviewResolvedAt,
        LocalDateTime deliveredAt         // 송장 조회로 배송 완료를 확인한 시각
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getAuctionId(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getProductName(),
                order.getProductImageUrl(),
                order.getFinalPrice(),
                order.getCommissionRateBps(),
                order.getCommission(),
                order.getSettlementAmount(),
                order.getNoAddressPenalty(),
                order.getStatus(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getAddress(),
                order.getDeliveryMemo(),
                order.getCourier(),
                order.getTrackingNo(),
                order.getCreatedAt(),
                order.getShippedAt(),
                order.getCompletedAt(),
                order.getAddressDeadlineAt(),
                order.getShipDeadlineAt(),
                order.getDelayStatus(),
                order.getDelayReason(),
                order.getCancelReason(),
                order.getCanceledAt(),
                order.getReviewReason(),
                order.getIssueType(),
                order.getIssueDetail(),
                order.getReviewRequestedAt(),
                order.getReviewResult(),
                order.getReviewResolvedAt(),
                order.getDeliveredAt()
        );
    }
}