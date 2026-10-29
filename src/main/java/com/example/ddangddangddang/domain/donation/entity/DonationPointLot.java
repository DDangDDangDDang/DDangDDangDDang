package com.example.ddangddangddang.domain.donation.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 적립 묶음: 한 번에 들어온 도네이션 마일리지. 묶음마다 유효기간이 있다.
 * 들어오는 곳(source)은 구매 확정 적립, 판매 확정 적립, 선물받음 세 가지이고, 어디서 왔든 같은 마일리지로 쓴다.
 * 선물·상점 구매에는 유효기간이 먼저 끝나는 묶음부터 쓰고(DonationLotUsage), 유효기간이 지나면 남은 양이 사라진다.
 * - (orderId, source) 유니크: 같은 주문으로 구매자·판매자에게 한 번씩만 적립한다. (구매 확정 이벤트가 두 번 와도 한 번)
 *   한 달 적립 한도에 걸린 주문은 0M 묶음으로 남겨 처리한 주문임을 표시한다.
 * - donationId 유니크: 선물 한 건으로 받는 묶음은 하나
 * - sellerId: 적립한 주문의 판매자 (선물받음은 null). 선물을 막는 데는 쓰지 않고, 나중에 자전 거래 같은 이상 패턴을 살펴볼 때 쓰려고 남긴다.
 */
@Getter
@Entity
@Table(
        name = "donation_point_lots",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_donation_lot_order_source", columnNames = {"order_id", "source"}),
                @UniqueConstraint(name = "uk_donation_lot_donation", columnNames = "donation_id")
        },
        indexes = {
                // 선물: 내 묶음 중 남은 것을 유효기간 순으로
                @Index(name = "idx_donation_lot_user_expires", columnList = "user_id, expires_at"),
                // 만료 스케줄러: 유효기간이 지난 묶음
                @Index(name = "idx_donation_lot_expires", columnList = "expires_at, remaining")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DonationPointLot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // MySQL ENUM이 아니라 문자열로 둔다. (값을 더할 때 ddl-auto가 ENUM 정의를 고치지 않음)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private DonationLotSource source;

    // 구매·판매 적립의 주문. 선물받음이면 null
    @Column(name = "order_id")
    private Long orderId;

    // 선물받음의 선물. 적립이면 null
    @Column(name = "donation_id")
    private Long donationId;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long remaining;

    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 곧 사라진다는 알림을 보냈는지 (한 번만 보낸다)
    @Column(name = "expiry_notified", nullable = false)
    private boolean expiryNotified;

    private DonationPointLot(Long userId, DonationLotSource source, Long orderId, Long donationId, Long sellerId,
                             long amount, LocalDateTime earnedAt, LocalDateTime expiresAt) {
        this.userId = userId;
        this.source = source;
        this.orderId = orderId;
        this.donationId = donationId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.remaining = amount;
        this.earnedAt = earnedAt;
        this.expiresAt = expiresAt;
    }

    /** 구매 확정 적립 (구매자) */
    public static DonationPointLot purchase(Long buyerId, Long orderId, Long sellerId, long amount, LocalDateTime earnedAt, LocalDateTime expiresAt) {
        return new DonationPointLot(buyerId, DonationLotSource.PURCHASE, orderId, null, sellerId, amount, earnedAt, expiresAt);
    }

    /** 판매 확정 적립 (판매자) */
    public static DonationPointLot sale(Long sellerId, Long orderId, long amount, LocalDateTime earnedAt, LocalDateTime expiresAt) {
        return new DonationPointLot(sellerId, DonationLotSource.SALE, orderId, null, sellerId, amount, earnedAt, expiresAt);
    }

    /** 선물받음 (판매자). 받은 날부터 적립과 같은 유효기간 */
    public static DonationPointLot received(Long sellerId, Long donationId, long amount, LocalDateTime receivedAt, LocalDateTime expiresAt) {
        return new DonationPointLot(sellerId, DonationLotSource.RECEIVE, null, donationId, null, amount, receivedAt, expiresAt);
    }

    /** 남은 양에서 최대 want만큼 쓴다. @return 실제로 쓴 양 */
    public long use(long want) {
        long used = Math.min(remaining, Math.max(0, want));
        this.remaining -= used;
        return used;
    }

    /** 유효기간이 지나 남은 양을 없앤다. @return 사라진 양 */
    public long expire() {
        long expired = remaining;
        this.remaining = 0;
        return expired;
    }

    public void markExpiryNotified() {
        this.expiryNotified = true;
    }
}
