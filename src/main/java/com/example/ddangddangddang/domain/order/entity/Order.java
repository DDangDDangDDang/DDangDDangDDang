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
import org.hibernate.annotations.ColumnDefault;

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
                @Index(name = "idx_orders_seller_id", columnList = "sellerId, id"),
                @Index(name = "idx_orders_status_address_deadline", columnList = "status, addressDeadlineAt"),
                @Index(name = "idx_orders_status_ship_deadline", columnList = "status, shipDeadlineAt"),
                @Index(name = "idx_orders_status_shipped_at", columnList = "status,shippedAt")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    public static final String UK_AUCTION_ID = "uk_orders_auction_id";
    public static final String UK_COURIER_TRACKING_NO = "uk_orders_courier_tracking_no";
    private static final int ADDRESS_DEADLINE_HOURS = 72;
    private static final int NO_ADDRESS_PENALTY_BPS = 1_000;
    private static final int SHIP_DEADLINE_DAYS = 7;
    private static final int DELAY_EXTENSION_DAYS = 7;

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

    /** 배송지 미입력 위약금률(bp, 1000 = 10%). 낙찰 때 고지한 조건이라 주문이 생길 때 정한다. 도입 전 주문은 0 */
    @Column(nullable = false)
    private int penaltyRateBps;

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

    // 자동 구매 확정이 실패 획수. 정해진 횟수를 넘으면 자동 확정 대상에서 빠지고 사람이 확인
    @ColumnDefault("0")
    @Column(nullable = false)
    private int autoConfirmFailCount;

    // 자동 구매 확정이 마지막으로 실패한 시간, 실패한 주문은 일정 시간이 지난 뒤에 다시 시도한다.
    private LocalDateTime autoConfirmLastFailedAt;

    private LocalDateTime completedAt;

    /** 배송지 입력 기한. 이 기능 도입 전 주문은 NULL일 수 있고, NULL이면 기한을 검사하지 않는다 */
    private LocalDateTime addressDeadlineAt;

    /** 발송 기한. 배송지를 처음 입력할 때 정하고, 고쳐도 늘어나지 않는다 */
    private LocalDateTime shipDeadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderDelayStatus delayStatus;

    @Column(length = 200)
    private String delayReason;

    private LocalDateTime delayRequestedAt;

    private LocalDateTime delayRespondedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrderCancelReason cancelReason;

    private LocalDateTime canceledAt;

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
        this.penaltyRateBps = NO_ADDRESS_PENALTY_BPS;
        this.addressDeadlineAt = LocalDateTime.now().plusHours(ADDRESS_DEADLINE_HOURS);
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

    /** 배송지 미입력으로 취소될 때 판매자에게 줄 위약금 (원 미만 버림). 나머지는 구매자에게 환불한다 */
    public long getNoAddressPenalty() {
        return CommissionPolicy.commissionOf(this.finalPrice, this.penaltyRateBps);
    }

    /** 판매자가 받을 금액 = 판매가 - 수수료. 구매 확정 때 이만큼 정산한다 */
    public long getSettlementAmount() {
        return this.finalPrice - this.commission;
    }

    /**
     * 배송지 입력. 발송 전(ESCROW)에는 여러 번 고칠 수 있다.
     */
    public void registerDeliveryAddress(String receiverName, String receiverPhone, String address, String deliveryMemo) {
        registerDeliveryAddress(receiverName, receiverPhone, address, deliveryMemo, LocalDateTime.now());
    }

    public void registerDeliveryAddress(String receiverName, String receiverPhone, String address, String deliveryMemo,
                                        LocalDateTime now) {
        if (status != OrderStatus.ESCROW) {
            throw new BusinessException(OrderErrorCode.CANNOT_REGISTER_ADDRESS);
        }
        if (hasDeliveryAddress() ? isShipOverdue(now) : isAddressOverdue(now)) {
            throw new BusinessException(OrderErrorCode.DEADLINE_PASSED);
        }
        if (!hasDeliveryAddress()) {
            this.shipDeadlineAt = now.plusDays(SHIP_DEADLINE_DAYS);
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
        ship(courier, trackingNo, LocalDateTime.now());
    }

    public void ship(Courier courier, String trackingNo, LocalDateTime now) {
        if (status != OrderStatus.ESCROW) {
            throw new BusinessException(OrderErrorCode.CANNOT_SHIP);
        }
        if (!hasDeliveryAddress()) {
            throw new BusinessException(OrderErrorCode.ADDRESS_REQUIRED);
        }
        if (isShipOverdue(now)) {
            throw new BusinessException(OrderErrorCode.DEADLINE_PASSED);
        }

        this.courier = courier;
        this.trackingNo = trackingNo;
        this.shippedAt = now;
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

    /** 배송지 입력 기한을 넘겼는지. 입력한 주문은 해당 없다 */
    public boolean isAddressOverdue(LocalDateTime now) {
        return !hasDeliveryAddress() && addressDeadlineAt != null && !now.isBefore(addressDeadlineAt);
    }

    /** 발송 기한을 넘겼는지 */
    public boolean isShipOverdue(LocalDateTime now) {
        return shipDeadlineAt != null && !now.isBefore(shipDeadlineAt);
    }

    /**
     * 기한 초과 취소: ESCROW -> CANCELED. 환불은 서비스에서 포인트 도메인에 요청한다.
     * 스케줄러가 상태를 확인한 뒤에만 부르므로, 다른 상태에서 오면 코드 오류다.
     */
    public void cancel(OrderCancelReason reason, LocalDateTime now) {
        if (status != OrderStatus.ESCROW) {
            throw new IllegalStateException("발송 전 주문만 취소할 수 있습니다: " + status);
        }
        this.cancelReason = reason;
        this.canceledAt = now;
        this.status = OrderStatus.CANCELED;
    }

    /** 판매자의 발송 지연 신청. 주문당 한 번만 할 수 있다 */
    public void requestDelay(String reason, LocalDateTime now) {
        if (status != OrderStatus.ESCROW || !hasDeliveryAddress() || delayStatus != null) {
            throw new BusinessException(OrderErrorCode.CANNOT_REQUEST_DELAY);
        }
        if (isShipOverdue(now)) {
            throw new BusinessException(OrderErrorCode.DEADLINE_PASSED);
        }
        this.delayStatus = OrderDelayStatus.PENDING;
        this.delayReason = reason;
        this.delayRequestedAt = now;
    }

    /** 구매자의 지연 승인: 현재 기한을 7일 늘린다 */
    public void approveDelay(LocalDateTime now) {
        respondDelay(OrderDelayStatus.APPROVED, now);
        if (shipDeadlineAt != null) {
            this.shipDeadlineAt = shipDeadlineAt.plusDays(DELAY_EXTENSION_DAYS);
        }
    }

    /** 구매자의 지연 거절: 기존 기한을 유지한다 */
    public void rejectDelay(LocalDateTime now) {
        respondDelay(OrderDelayStatus.REJECTED, now);
    }

    private void respondDelay(OrderDelayStatus result, LocalDateTime now) {
        if (status != OrderStatus.ESCROW || delayStatus != OrderDelayStatus.PENDING) {
            throw new BusinessException(OrderErrorCode.CANNOT_RESPOND_DELAY);
        }
        if (isShipOverdue(now)) {
            throw new BusinessException(OrderErrorCode.DEADLINE_PASSED);
        }
        this.delayStatus = result;
        this.delayRespondedAt = now;
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

    // 자동 구매 확정 실패 획수를 1씩 올린다
    public void recordAutoConfirmFailure(LocalDateTime now){
        this.autoConfirmFailCount++;
        this.autoConfirmLastFailedAt = now;
    }
}