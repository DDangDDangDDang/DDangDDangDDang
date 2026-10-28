package com.example.ddangddangddang.domain.donation.dto;

import java.time.LocalDateTime;

/**
 * 내 도네이션 마일리지 (환전할 수 없는 응원용 마일리지)
 * - balance: 쓸 수 있는 마일리지. 구매·판매 적립과 선물받은 마일리지를 구분하지 않고 선물·상점에 쓴다. (모두 유효기간 있음)
 * - expiringSoon: 곧(7일 안에) 사라질 마일리지, nextExpiresAt: 가장 먼저 사라지는 마일리지의 유효기간 (없으면 null)
 * - earnedThisMonth / saleEarnedThisMonth: 이번 달 구매 확정·판매 확정으로 적립한 마일리지
 * - monthlyEarnLimit: 한 달 적립 한도 (구매 적립·판매 적립 각각)
 */
public record DonationWalletResponse(
        long balance,
        long expiringSoon,
        LocalDateTime nextExpiresAt,
        long earnedThisMonth,
        long saleEarnedThisMonth,
        long monthlyEarnLimit
) {
}
