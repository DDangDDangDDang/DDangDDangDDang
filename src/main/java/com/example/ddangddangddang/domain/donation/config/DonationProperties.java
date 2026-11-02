package com.example.ddangddangddang.domain.donation.config;

import com.example.ddangddangddang.domain.donation.policy.DonationPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 도네이션 포인트 정책의 처음 값. 운영 중에는 관리자가 DB(donation_policies)에서 바꾸고, DB에 정책이 하나도 없을 때만 이 값을 쓴다.
 * (DonationPolicies.current) 모든 값에 기본값이 있어 application.yml에 적지 않아도 동작한다.
 *
 * <pre>
 * donation.earn-rate-percent              구매 확정 시 낙찰가의 몇 %를 구매자·판매자에게 각각 적립할지. 기본 1 (%)
 * donation.max-earn-per-order             주문 한 건으로 적립할 수 있는 최대 포인트. 기본 1,000
 * donation.monthly-earn-limit             한 달(1일~말일)에 적립할 수 있는 최대 포인트 (구매 적립·판매 적립 각각). 기본 10,000
 * donation.point-valid-for                적립·선물받은 포인트의 유효기간. 기본 90일
 * donation.expiring-notice-before         유효기간이 이만큼 남으면 곧 사라진다고 알린다. 기본 7일
 * donation.min-gift                       한 번에 선물할 수 있는 최소 포인트. 기본 100
 * donation.gift-unit                      선물 단위 (이 단위로만 선물). 기본 100
 * donation.max-gift                       한 번에 선물할 수 있는 최대 포인트. 기본 5,000
 * donation.monthly-gift-limit-per-seller  한 계정이 판매자 한 명에게 한 달에 선물할 수 있는 최대 포인트. 기본 5,000
 * donation.trust-window                   판매자 프로필의 "응원" 지표를 세는 기간. 기본 90일
 * </pre>
 */
@ConfigurationProperties(prefix = "donation")
public record DonationProperties(
        Integer earnRatePercent,
        Long maxEarnPerOrder,
        Long monthlyEarnLimit,
        Duration pointValidFor,
        Duration expiringNoticeBefore,
        Long minGift,
        Long giftUnit,
        Long maxGift,
        Long monthlyGiftLimitPerSeller,
        Duration trustWindow
) {
    public DonationProperties {
        earnRatePercent = earnRatePercent == null ? 1 : earnRatePercent;
        maxEarnPerOrder = maxEarnPerOrder == null ? 1_000L : maxEarnPerOrder;
        monthlyEarnLimit = monthlyEarnLimit == null ? 10_000L : monthlyEarnLimit;
        pointValidFor = pointValidFor == null ? Duration.ofDays(90) : pointValidFor;
        expiringNoticeBefore = expiringNoticeBefore == null ? Duration.ofDays(7) : expiringNoticeBefore;
        minGift = minGift == null ? 100L : minGift;
        giftUnit = giftUnit == null ? 100L : giftUnit;
        maxGift = maxGift == null ? 5_000L : maxGift;
        monthlyGiftLimitPerSeller = monthlyGiftLimitPerSeller == null ? 5_000L : monthlyGiftLimitPerSeller;
        trustWindow = trustWindow == null ? Duration.ofDays(90) : trustWindow;
    }

    /** 기본값만 쓰는 설정 (테스트) */
    public static DonationProperties defaults() {
        return new DonationProperties(null, null, null, null, null, null, null, null, null, null);
    }

    /** 처음 정책 (기간은 일 단위로) */
    public DonationPolicy toPolicy() {
        return new DonationPolicy(earnRatePercent, maxEarnPerOrder, monthlyEarnLimit,
                (int) pointValidFor.toDays(), (int) expiringNoticeBefore.toDays(),
                minGift, giftUnit, maxGift, monthlyGiftLimitPerSeller,
                (int) trustWindow.toDays());
    }
}
