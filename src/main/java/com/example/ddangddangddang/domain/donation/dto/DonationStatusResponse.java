package com.example.ddangddangddang.domain.donation.dto;

/**
 * 선물 창에 보여 줄 정보 (이 방송의 판매자에게 지금 선물할 수 있는지).
 * - giftableBalance: 지금 선물할 수 있는 내 포인트 (유효기간이 지난 것은 빠짐)
 * - monthlyLimit / givenThisMonth / remainingThisMonth: 이 판매자에게 한 달에 선물할 수 있는 포인트, 이번 달 선물한 포인트, 남은 포인트
 * - minGift / giftUnit / maxGift: 한 번에 선물할 수 있는 최소 포인트, 단위, 최대 포인트
 */
public record DonationStatusResponse(
        Long sellerId,
        long giftableBalance,
        long monthlyLimit,
        long givenThisMonth,
        long remainingThisMonth,
        long minGift,
        long giftUnit,
        long maxGift
) {
}
