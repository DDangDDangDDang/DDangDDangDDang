package com.example.ddangddangddang.domain.donation.dto;

/**
 * 선물 결과.
 * - balance: 선물한 뒤 남은 내 마일리지
 * - remainingThisMonth: 이 판매자에게 이번 달 더 선물할 수 있는 포인트
 */
public record DonationResponse(
        Long donationId,
        long amount,
        long balance,
        long remainingThisMonth
) {
}
