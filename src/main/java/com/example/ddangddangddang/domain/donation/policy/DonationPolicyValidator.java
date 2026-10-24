package com.example.ddangddangddang.domain.donation.policy;

import com.example.ddangddangddang.domain.donation.exception.DonationErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;

/**
 * 관리자가 바꾸려는 정책이 말이 되는지 확인한다. 잘못된 값이 들어가면 선물·적립이 모두 막힐 수 있어 저장 전에 거른다.
 */
final class DonationPolicyValidator {

    private DonationPolicyValidator() {
    }

    static void validate(DonationPolicy p) {
        check(p.earnRatePercent() >= 0 && p.earnRatePercent() <= 100, "적립 비율은 0~100%여야 합니다.");
        check(p.maxEarnPerOrder() >= 0, "주문당 최대 적립은 0 이상이어야 합니다.");
        check(p.monthlyEarnLimit() >= 0, "한 달 적립 한도는 0 이상이어야 합니다.");
        check(p.pointValidDays() >= 1 && p.pointValidDays() <= 3650, "유효기간은 1~3650일이어야 합니다.");
        check(p.expiringNoticeDays() >= 0 && p.expiringNoticeDays() < p.pointValidDays(), "만료 알림은 0일 이상, 유효기간보다 짧아야 합니다.");
        check(p.giftUnit() >= 1, "선물 단위는 1 이상이어야 합니다.");
        check(p.minGift() >= p.giftUnit() && p.minGift() % p.giftUnit() == 0, "최소 선물은 선물 단위 이상이고 선물 단위로 나누어떨어져야 합니다.");
        check(p.maxGift() >= p.minGift() && p.maxGift() % p.giftUnit() == 0, "최대 선물은 최소 선물 이상이고 선물 단위로 나누어떨어져야 합니다.");
        check(p.monthlyGiftLimitPerSeller() >= 0, "판매자 한 명에게 한 달 선물 한도는 0 이상이어야 합니다.");
        check(p.trustWindowDays() >= 1 && p.trustWindowDays() <= 3650, "응원 지표 기간은 1~3650일이어야 합니다.");
        check(p.powerSellerPrice() >= 1, "파워 판매자 가격은 1 이상이어야 합니다.");
        check(p.powerSellerDays() >= 1 && p.powerSellerDays() <= 365, "파워 판매자 기간은 1~365일이어야 합니다.");
        check(p.promotedLiveSlots() >= 0 && p.promotedLiveSlots() <= 20, "라이브 목록 맨 위 칸 수는 0~20이어야 합니다.");
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new BusinessException(DonationErrorCode.INVALID_POLICY, message);
        }
    }
}
