package com.example.ddangddangddang.domain.donation.policy;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도네이션 포인트 정책의 한 버전. 관리자가 정책을 바꿀 때마다 새 행을 쌓고 고치거나 지우지 않는다.
 * 가장 최근 행(id가 가장 큰 것)이 지금 정책이다. 이전 행은 "언제 어떤 정책이었는지" 기록으로 남는다. (적립·선물 문의, 분쟁)
 */
@Getter
@Entity
@Table(name = "donation_policies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DonationPolicyVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "earn_rate_percent", nullable = false)
    private int earnRatePercent;

    @Column(name = "max_earn_per_order", nullable = false)
    private long maxEarnPerOrder;

    @Column(name = "monthly_earn_limit", nullable = false)
    private long monthlyEarnLimit;

    @Column(name = "point_valid_days", nullable = false)
    private int pointValidDays;

    @Column(name = "expiring_notice_days", nullable = false)
    private int expiringNoticeDays;

    @Column(name = "min_gift", nullable = false)
    private long minGift;

    @Column(name = "gift_unit", nullable = false)
    private long giftUnit;

    @Column(name = "max_gift", nullable = false)
    private long maxGift;

    @Column(name = "monthly_gift_limit_per_seller", nullable = false)
    private long monthlyGiftLimitPerSeller;

    @Column(name = "trust_window_days", nullable = false)
    private int trustWindowDays;



    // 바꾼 관리자
    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    // 바꾼 이유 (선택). 예: "11월 적립 2% 이벤트"
    @Column(length = 200)
    private String memo;

    private DonationPolicyVersion(DonationPolicy policy, Long changedBy, String memo) {
        this.earnRatePercent = policy.earnRatePercent();
        this.maxEarnPerOrder = policy.maxEarnPerOrder();
        this.monthlyEarnLimit = policy.monthlyEarnLimit();
        this.pointValidDays = policy.pointValidDays();
        this.expiringNoticeDays = policy.expiringNoticeDays();
        this.minGift = policy.minGift();
        this.giftUnit = policy.giftUnit();
        this.maxGift = policy.maxGift();
        this.monthlyGiftLimitPerSeller = policy.monthlyGiftLimitPerSeller();
        this.trustWindowDays = policy.trustWindowDays();
        this.changedBy = changedBy;
        this.memo = memo;
    }

    public static DonationPolicyVersion of(DonationPolicy policy, Long changedBy, String memo) {
        return new DonationPolicyVersion(policy, changedBy, memo);
    }

    public DonationPolicy toPolicy() {
        return new DonationPolicy(earnRatePercent, maxEarnPerOrder, monthlyEarnLimit, pointValidDays, expiringNoticeDays,
                minGift, giftUnit, maxGift, monthlyGiftLimitPerSeller, trustWindowDays);
    }
}
