package com.example.ddangddangddang.domain.order.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_buyer_id", columnList = "buyerId, id"),
        @Index(name = "idx_orders_seller_id", columnList = "sellerId, id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자에게 보여주는 주문번호 */
    @Column(nullable = false, unique = true, length = 40)
    private String orderNo;

    /** 경매 하나당 주문은 하나만 생긴다 */
    @Column(nullable = false, unique = true)
    private Long auctionId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long sellerId;

    /** 낙찰 시점 상품 정보 스냅샷. 이후 상품이 바뀌거나 숨겨져도 주문 내역은 그대로 보여준다. */
    @Column(nullable = false, length = 100)
    private String productName;

    @Column(length = 500)
    private String productImageUrl;

    @Column(nullable = false)
    private long finalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(length = 50)
    private String trackingNo;

    private LocalDateTime shippedAt;

    private LocalDateTime completedAt;

    private Order(Long auctionId, Long buyerId, Long sellerId, String productName,
                  String productImageUrl, long finalPrice) {
        this.orderNo = UUID.randomUUID().toString();
        this.auctionId = auctionId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.productName = productName;
        this.productImageUrl = productImageUrl;
        this.finalPrice = finalPrice;
        this.status = OrderStatus.ESCROW;
    }

    public static Order create(Long auctionId, Long buyerId, Long sellerId, String productName,
                               String productImageUrl, long finalPrice) {
        return new Order(auctionId, buyerId, sellerId, productName, productImageUrl, finalPrice);
    }

    public boolean isBuyer(Long userId) {
        return buyerId.equals(userId);
    }

    public boolean isSeller(Long userId) {
        return sellerId.equals(userId);
    }

    public boolean isParticipant(Long userId) {
        return isBuyer(userId) || isSeller(userId);
    }
}