package com.example.ddangddangddang.domain.order.dto;

/**
 * 경매 도메인이 낙찰(또는 즉시구매)을 처리하면서 주문을 만들 때 넘기는 값.
 * 상품명, 이미지는 낙찰 시점 값으로 넘겨야 이후 상품이 바뀌어도 주문 내역이 유지된다.
 */
public record OrderCreateCommand(
        Long auctionId,
        Long buyerId,
        Long sellerId,
        String productName,
        String productImageUrl,
        long finalPrice
) {
}