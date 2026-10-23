package com.example.ddangddangddang.domain.donation.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 적립 묶음: 구매 확정 한 건으로 적립한 도네이션 포인트. 묶음마다 유효기간이 있다.
 * 선물할 때는 유효기간이 먼저 끝나는 묶음부터 쓰고(DonationLotUsage), 유효기간이 지나면 남은 양이 사라진다.
 * - orderId 유니크: 같은 주문으로 두 번 적립하지 않는다. (구매 확정 이벤트가 두 번 와도 한 번)
 * - sellerId: 이 주문의 판매자. 이 판매자와의 거래로 받은 포인트는 이 판매자에게 선물할 수 없다. (자전 거래 막기)
 */
@Getter
@Entity
@Table(
        name = "donation_point_lots",
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

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "seller_id", nullable = false)
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

    private DonationPointLot(Long userId, Long orderId, Long sellerId, long amount, LocalDateTime earnedAt, LocalDateTime expiresAt) {
        this.userId = userId;
        this.orderId = orderId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.remaining = amount;
        this.earnedAt = earnedAt;
        this.expiresAt = expiresAt;
    }

    public static DonationPointLot earn(Long userId, Long orderId, Long sellerId, long amount, LocalDateTime earnedAt, LocalDateTime expiresAt) {
        return new DonationPointLot(userId, orderId, sellerId, amount, earnedAt, expiresAt);
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
