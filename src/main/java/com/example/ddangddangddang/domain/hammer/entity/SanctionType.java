package com.example.ddangddangddang.domain.hammer.entity;

import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;

/**
 * 정지 종류와 그 정지를 부르는 본인 책임 취소 사유.
 */
public enum SanctionType {
    BID_BAN(OrderCancelReason.BUYER_NO_ADDRESS),       // 입찰·즉시 구매 정지 (구매자 책임: 배송지 미입력)
    LISTING_BAN(OrderCancelReason.SELLER_NOT_SHIPPED); // 경매 등록 정지 (판매자 책임: 미발송)

    private final OrderCancelReason cause;

    SanctionType(OrderCancelReason cause) {
        this.cause = cause;
    }

    public OrderCancelReason cause() {
        return cause;
    }
}
