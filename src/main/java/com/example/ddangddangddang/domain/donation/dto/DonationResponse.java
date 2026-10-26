package com.example.ddangddangddang.domain.donation.dto;

/**
 * 선물 결과.
 * - giftableBalance: 선물한 뒤 남은 선물할 포인트
 * - remainingThisMonth: 이 판매자에게 이번 달 더 선물할 수 있는 포인트
 */
public record DonationResponse(
        Long donationId,
        long amount,
        long giftableBalance,
        long remainingThisMonth
) {
}
