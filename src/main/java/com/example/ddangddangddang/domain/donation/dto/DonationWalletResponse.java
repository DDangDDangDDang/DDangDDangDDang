package com.example.ddangddangddang.domain.donation.dto;

import java.time.LocalDateTime;

/**
 * 내 도네이션 포인트 (환전할 수 없는 응원용 포인트)
 * - giftableBalance: 판매자에게 선물할 수 있는 포인트 (구매 확정으로 적립, 유효기간 있음)
 * - receivedBalance: 선물받은 포인트 (판매자 상점에서 파워 판매자 등을 살 때 씀)
 * - expiringSoon: 곧(7일 안에) 사라질 포인트, nextExpiresAt: 가장 먼저 사라지는 포인트의 유효기간 (없으면 null)
 * - earnedThisMonth / monthlyEarnLimit: 이번 달 적립한 포인트와 한 달 적립 한도
 */
public record DonationWalletResponse(
        long giftableBalance,
        long receivedBalance,
        long expiringSoon,
        LocalDateTime nextExpiresAt,
        long earnedThisMonth,
        long monthlyEarnLimit
) {
}
