package com.example.ddangddangddang.domain.donation.policy;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자: 도네이션 포인트 정책 바꾸기. 모든 값을 보낸다. (바꾸지 않는 값도 지금 값 그대로)
 * 항목 설명은 DonationPolicy, 값 범위는 DonationPolicyValidator. memo는 바꾼 이유 (선택, 200자 이하)
 */
public record DonationPolicyRequest(
        @NotNull Integer earnRatePercent,
        @NotNull Long maxEarnPerOrder,
        @NotNull Long monthlyEarnLimit,
        @NotNull Integer pointValidDays,
        @NotNull Integer expiringNoticeDays,
        @NotNull Long minGift,
        @NotNull Long giftUnit,
        @NotNull Long maxGift,
        @NotNull Long monthlyGiftLimitPerSeller,
        @NotNull Integer trustWindowDays,
        @Size(max = 200, message = "바꾼 이유는 200자 이하입니다.")
        String memo
) {
    DonationPolicy toPolicy() {
        return new DonationPolicy(earnRatePercent, maxEarnPerOrder, monthlyEarnLimit, pointValidDays, expiringNoticeDays,
                minGift, giftUnit, maxGift, monthlyGiftLimitPerSeller, trustWindowDays);
    }
}
