package com.example.ddangddangddang.domain.order.dto;

import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;

import java.time.LocalDateTime;

/** 주문 목록의 한 줄 */
public record OrderSummaryResponse(
        Long orderId,
        String orderNo,
        String productName,
        String productImageUrl,
        long finalPrice,
        long settlementAmount,  // 판매자가 받을 금액 (판매가 - 수수료)
        OrderStatus status,
        LocalDateTime createdAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNo(),
                order.getProductName(),
                order.getProductImageUrl(),
                order.getFinalPrice(),
                order.getSettlementAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}