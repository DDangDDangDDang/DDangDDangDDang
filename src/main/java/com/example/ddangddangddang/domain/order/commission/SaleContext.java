package com.example.ddangddangddang.domain.order.commission;

/**
 * 수수료를 정할 때 보는 거래 정보. 할인 규칙(CommissionDiscount)이 이 값을 보고 할인 여부를 정한다.
 * 할인 규칙이 더 많은 정보(상품 카테고리, 판매자 등급 등)가 필요해지면 여기에 필드를 더한다.
 */
public record SaleContext(
        Long auctionId,
        Long sellerId,
        Long buyerId,
        long finalPrice
) {
}
