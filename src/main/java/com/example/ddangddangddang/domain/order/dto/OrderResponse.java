package com.example.ddangddangddang.domain.order.dto;

import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
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
        OrderStatus status,
        String receiverName,
        String receiverPhone,
        String address,
        String deliveryMemo,
        Courier courier,
        String trackingNo,
        LocalDateTime createdAt,
        LocalDateTime shippedAt,
        LocalDateTime completedAt
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
                order.getStatus(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getAddress(),
                order.getDeliveryMemo(),
                order.getCourier(),
                order.getTrackingNo(),
                order.getCreatedAt(),
                order.getShippedAt(),
                order.getCompletedAt()
        );
    }
}