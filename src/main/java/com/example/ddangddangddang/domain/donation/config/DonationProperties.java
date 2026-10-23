package com.example.ddangddangddang.domain.donation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 도네이션 포인트 설정. 모든 값에 기본값이 있어 application.yml에 적지 않아도 동작한다.
 * 운영하며 바꿀 수 있도록 숫자는 모두 여기에 둔다.
 *
 * <pre>
 * donation.earn-rate-percent              구매 확정 시 낙찰가의 몇 %를 적립할지. 기본 1 (%)
 * donation.max-earn-per-order             주문 한 건으로 적립할 수 있는 최대 포인트. 기본 1,000
 * donation.monthly-earn-limit             한 달(1일~말일)에 적립할 수 있는 최대 포인트. 기본 10,000
 * donation.point-valid-for                적립한 포인트의 유효기간. 기본 90일
 * donation.expiring-notice-before         유효기간이 이만큼 남으면 곧 사라진다고 알린다. 기본 7일
 * donation.min-gift                       한 번에 선물할 수 있는 최소 포인트. 기본 100
 * donation.gift-unit                      선물 단위 (이 단위로만 선물). 기본 100
 * donation.max-gift                       한 번에 선물할 수 있는 최대 포인트. 기본 5,000
 * donation.monthly-gift-limit-per-seller  한 계정이 판매자 한 명에게 한 달에 선물할 수 있는 최대 포인트. 기본 5,000
 * donation.trust-window                   판매자 프로필의 "응원" 지표를 세는 기간. 기본 90일
 * donation.power-seller-price             파워 판매자 가격 (받은 포인트). 기본 30,000
 * donation.power-seller-period            파워 판매자 기간. 기본 30일
 * donation.promoted-live-slots            라이브 목록 맨 위에 올리는 파워 판매자 방송 수. 기본 4
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
        Duration trustWindow,
        Long powerSellerPrice,
        Duration powerSellerPeriod,
        Integer promotedLiveSlots
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
        powerSellerPrice = powerSellerPrice == null ? 30_000L : powerSellerPrice;
        powerSellerPeriod = powerSellerPeriod == null ? Duration.ofDays(30) : powerSellerPeriod;
        promotedLiveSlots = promotedLiveSlots == null ? 4 : promotedLiveSlots;
    }

    /** 기본값만 쓰는 설정 (테스트) */
    public static DonationProperties defaults() {
        return new DonationProperties(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** 낙찰가에 대한 적립 포인트 (한 달 한도는 따로 본다). 1P 미만은 버린다. */
    public long earnFor(long finalPrice) {
        return Math.min(maxEarnPerOrder, Math.max(0, finalPrice) * earnRatePercent / 100);
    }
}
