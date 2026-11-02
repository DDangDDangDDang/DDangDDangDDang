package com.example.ddangddangddang.domain.order.entity;

import com.example.ddangddangddang.domain.order.commission.CommissionPolicy;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "orders",
        uniqueConstraints = {
        @UniqueConstraint(name = Order.UK_AUCTION_ID, columnNames = "auctionId"),
        @UniqueConstraint(name = Order.UK_COURIER_TRACKING_NO, columnNames = {"courier", "trackingNo"})},
        indexes = {
                @Index(name = "idx_orders_buyer_id", columnList = "buyerId, id"),
                @Index(name = "idx_orders_seller_id", columnList = "sellerId, id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    public static final String UK_AUCTION_ID = "uk_orders_auction_id";
    public static final String UK_COURIER_TRACKING_NO = "uk_orders_courier_tracking_no";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사용자에게 보여주는 주문번호
     */
    @Column(nullable = false, unique = true, length = 40)
    private String orderNo;

    /**
     * 경매 하나당 주문은 하나만 생긴다
     */
    @Column(nullable = false)
    private Long auctionId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long sellerId;

    /**
     * 낙찰 시점 상품 정보 스냅샷. 이후 상품이 바뀌거나 숨겨져도 주문 내역은 그대로 보여준다.
     */
    @Column(nullable = false, length = 100)
    private String productName;

    @Column(length = 500)
    private String productImageUrl;

    @Column(nullable = false)
    private long finalPrice;

    /** 판매 수수료율(bp, 500 = 5%). 주문이 생길 때 정한다. 수수료 도입 전 주문은 0 */
    @Column(nullable = false)
    private int commissionRateBps;

    /** 판매 수수료(포인트). 구매 확정 때 판매가에서 이만큼 떼고 판매자에게 정산한다. 수수료 도입 전 주문은 0 */
    @Column(nullable = false)
    private long commission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /*
     * 배송지. 낙찰자가 발송 전에 입력하고, 판매자는 이 정보로 송장을 만든다.
     * 주문에 직접 저장(스냅샷)해서, 나중에 회원 정보가 바뀌어도 이미 보낸 주문의 배송지는 그대로 남는다.
     */
    @Column(length = 20)
    private String receiverName;

    @Column(length = 11)
    private String receiverPhone;

    @Column(length = 200)
    private String address;

    @Column(length = 100)
    private String deliveryMemo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Courier courier;

    @Column(length = 50)
    private String trackingNo;

    private LocalDateTime shippedAt;

    private LocalDateTime completedAt;

    private Order(Long auctionId, Long buyerId, Long sellerId, String productName,
                  String productImageUrl, long finalPrice, int commissionRateBps) {
        this.orderNo = UUID.randomUUID().toString();
        this.auctionId = auctionId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.productName = productName;
        this.productImageUrl = productImageUrl;
        this.finalPrice = finalPrice;
        this.commissionRateBps = commissionRateBps;
        this.commission = CommissionPolicy.commissionOf(finalPrice, commissionRateBps);
        this.status = OrderStatus.ESCROW;
    }

    /** 수수료 없이 주문을 만든다 (수수료율 0, 테스트용) */
    public static Order create(Long auctionId, Long buyerId, Long sellerId, String productName,
                               String productImageUrl, long finalPrice) {
        return create(auctionId, buyerId, sellerId, productName, productImageUrl, finalPrice, 0);
    }

    /** 주문을 만든다. 수수료 = 판매가 × commissionRateBps / 10000 (원 미만 버림) */
    public static Order create(Long auctionId, Long buyerId, Long sellerId, String productName,
                               String productImageUrl, long finalPrice, int commissionRateBps) {
        if (commissionRateBps < 0 || commissionRateBps >= CommissionPolicy.BPS_DENOMINATOR) {
            throw new IllegalArgumentException("수수료율이 올바르지 않습니다: " + commissionRateBps);
        }
        return new Order(auctionId, buyerId, sellerId, productName, productImageUrl, finalPrice, commissionRateBps);
    }

    /** 판매자가 받을 금액 = 판매가 - 수수료. 구매 확정 때 이만큼 정산한다 */
    public long getSettlementAmount() {
        return this.finalPrice - this.commission;
    }

    /**
     * 배송지 입력. 발송 전(ESCROW)에는 여러 번 고칠 수 있다.
     */
    public void registerDeliveryAddress(String receiverName, String receiverPhone, String address, String deliveryMemo) {
        if (status != OrderStatus.ESCROW) {
            throw new BusinessException(OrderErrorCode.CANNOT_REGISTER_ADDRESS);
        }
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.address = address;
        this.deliveryMemo = deliveryMemo;
    }

    public boolean hasDeliveryAddress() {
        return address != null;
    }

    /**
     * 발송 처리: ESCROW -> SHIPPING
     */
    public void ship(Courier courier, String trackingNo) {
        if (status != OrderStatus.ESCROW) {
            throw new BusinessException(OrderErrorCode.CANNOT_SHIP);
        }
        if (!hasDeliveryAddress()) {
            throw new BusinessException(OrderErrorCode.ADDRESS_REQUIRED);
        }

        this.courier = courier;
        this.trackingNo = trackingNo;
        this.shippedAt = LocalDateTime.now();
        this.status = OrderStatus.SHIPPING;
    }

    /**
     * 구매 확정: SHIPPING -> COMPLETED. 정산은 서비스에서 포인트 도메인에 요청한다.
     */
    public void confirm() {
        if (status != OrderStatus.SHIPPING) {
            throw new BusinessException(OrderErrorCode.CANNOT_CONFIRM);
        }
        this.completedAt = LocalDateTime.now();
        this.status = OrderStatus.COMPLETED;
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