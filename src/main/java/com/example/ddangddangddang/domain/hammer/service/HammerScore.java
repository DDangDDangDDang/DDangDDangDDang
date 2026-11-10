package com.example.ddangddangddang.domain.hammer.service;

import com.example.ddangddangddang.domain.hammer.entity.HammerGrade;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 망치 점수 계산. DB를 모르는 순수 계산이라 기준을 바꿀 때 여기만 보면 된다.
 *
 * - 시작 50점, 0~100 사이
 * - 구매 확정 거래 +1 (구매자·판매자 모두), 판매자로 받은 리뷰 5점 +1 · 4점 +0.5 · 3점 0 · 2점 -1 · 1점 -2
 * - 같은 상대와의 거래는 30일에 한 번만 올려 준다. (짜고 올리기 방지, 나쁜 리뷰는 그래도 깎는다)
 * - 배송지 미입력 낙찰 취소(구매자 책임), 판매자 미발송 취소(판매자 책임)는 건마다 -5
 * 어느 기간의 기록을 넘길지는 부르는 쪽이 정한다. (최근 12개월)
 */
public final class HammerScore {

    static final double START = 50;
    static final double MIN = 0;
    static final double MAX = 100;
    static final double COMPLETED_DEAL = 1;
    static final double RESPONSIBLE_CANCEL = -5;
    static final int SAME_PARTNER_DAYS = 30;
    static final int NEWBIE_DEAL_COUNT = 3;

    private HammerScore() {
    }

    /**
     * 구매 확정 거래 하나. 판매자로 한 거래면 그 거래에 받은 리뷰 점수(없으면 null)를 함께 넘긴다.
     */
    public record Deal(Long partnerId, LocalDateTime completedAt, boolean asSeller, Integer reviewRating) {

        public static Deal asBuyer(Long sellerId, LocalDateTime completedAt) {
            return new Deal(sellerId, completedAt, false, null);
        }

        public static Deal asSeller(Long buyerId, LocalDateTime completedAt, Integer reviewRating) {
            return new Deal(buyerId, completedAt, true, reviewRating);
        }

        double review() {
            if (!asSeller || reviewRating == null) {
                return 0;
            }
            return switch (reviewRating) {
                case 5 -> 1;
                case 4 -> 0.5;
                case 2 -> -1;
                case 1 -> -2;
                default -> 0;
            };
        }
    }

    public static double score(List<Deal> deals, long noAddressCancels, long notShippedCancels) {
        double score = START;
        Map<Long, LocalDateTime> lastCounted = new HashMap<>();
        for (Deal deal : deals.stream().sorted(Comparator.comparing(Deal::completedAt)).toList()) {
            LocalDateTime last = lastCounted.get(deal.partnerId());
            boolean counted = last == null || !deal.completedAt().isBefore(last.plusDays(SAME_PARTNER_DAYS));
            if (counted) {
                lastCounted.put(deal.partnerId(), deal.completedAt());
                score += COMPLETED_DEAL + deal.review();
            } else {
                score += Math.min(0, deal.review());
            }
        }
        score += RESPONSIBLE_CANCEL * (noAddressCancels + notShippedCancels);
        return Math.max(MIN, Math.min(MAX, score));
    }

    /** @param completedDealCount 지금까지 구매 확정한 거래 수 (기간 제한 없음) */
    public static HammerGrade grade(double score, long completedDealCount) {
        if (completedDealCount < NEWBIE_DEAL_COUNT) {
            return HammerGrade.NEWBIE;
        }
        if (score >= 80) {
            return HammerGrade.DIAMOND;
        }
        if (score >= 60) {
            return HammerGrade.GOLD;
        }
        if (score >= 45) {
            return HammerGrade.SILVER;
        }
        if (score >= 30) {
            return HammerGrade.STONE;
        }
        return HammerGrade.WOOD;
    }
}
