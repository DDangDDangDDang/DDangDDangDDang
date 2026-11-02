package com.example.ddangddangddang.domain.donation.policy;

import java.time.Duration;

/**
 * 도네이션 포인트 정책 (지금 적용 중인 값). 운영자가 관리자 화면에서 바꾸고(DB donation_policies), 서비스는 DonationPolicies.current()로 읽는다.
 * DB에 정책이 아직 없으면 설정 기본값(DonationProperties)을 쓴다.
 *
 * - earnRatePercent / maxEarnPerOrder / monthlyEarnLimit: 구매 확정 시 구매자·판매자에게 각각 적립하는 비율(%), 주문당 최대, 한 달(1일~말일) 최대 (구매 적립·판매 적립 따로 셈)
 * - pointValidDays / expiringNoticeDays: 적립·선물받은 마일리지의 유효기간(일), 사라지기 며칠 전에 알릴지
 * - minGift / giftUnit / maxGift: 한 번에 선물할 수 있는 최소·단위·최대
 * - monthlyGiftLimitPerSeller: 한 계정이 판매자 한 명에게 한 달에 선물할 수 있는 최대
 * - trustWindowDays: 판매자 프로필의 응원 지표를 세는 기간(일)
 * 파워 판매자는 마일리지 상점의 고정 가격 상품이라 여기에 없다. (ShopItem.POWER_SELLER)
 */
public record DonationPolicy(
        int earnRatePercent,
        long maxEarnPerOrder,
        long monthlyEarnLimit,
        int pointValidDays,
        int expiringNoticeDays,
        long minGift,
        long giftUnit,
        long maxGift,
        long monthlyGiftLimitPerSeller,
        int trustWindowDays
) {

    /** 낙찰가에 대한 적립 포인트 (한 달 한도는 따로 본다). 1P 미만은 버린다. */
    public long earnFor(long finalPrice) {
        return Math.min(maxEarnPerOrder, Math.max(0, finalPrice) * earnRatePercent / 100);
    }

    public Duration pointValidFor() {
        return Duration.ofDays(pointValidDays);
    }

    public Duration expiringNoticeBefore() {
        return Duration.ofDays(expiringNoticeDays);
    }

    public Duration trustWindow() {
        return Duration.ofDays(trustWindowDays);
    }
}
