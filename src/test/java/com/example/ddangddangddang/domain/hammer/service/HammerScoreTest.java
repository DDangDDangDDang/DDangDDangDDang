package com.example.ddangddangddang.domain.hammer.service;

import com.example.ddangddangddang.domain.hammer.entity.HammerGrade;
import com.example.ddangddangddang.domain.hammer.service.HammerScore.Deal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HammerScoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Test
    @DisplayName("거래도 취소도 없으면 시작 점수 50")
    void startScore() {
        assertThat(HammerScore.score(List.of(), 0, 0)).isEqualTo(50.0);
    }

    @Test
    @DisplayName("구매 확정 거래는 상대가 다르면 건마다 +1")
    void completedDealsAddOne() {
        List<Deal> deals = List.of(
                Deal.asBuyer(10L, NOW.minusDays(3)),
                Deal.asSeller(11L, NOW.minusDays(2), null),
                Deal.asBuyer(12L, NOW.minusDays(1)));

        assertThat(HammerScore.score(deals, 0, 0)).isEqualTo(53.0);
    }

    @Test
    @DisplayName("판매자로 받은 리뷰는 5점 +1, 4점 +0.5, 3점 0, 2점 -1, 1점 -2를 더한다")
    void reviewRatingAdjustsSellerScore() {
        List<Deal> deals = List.of(
                Deal.asSeller(10L, NOW.minusDays(5), 5),   // +1 +1
                Deal.asSeller(11L, NOW.minusDays(4), 4),   // +1 +0.5
                Deal.asSeller(12L, NOW.minusDays(3), 3),   // +1
                Deal.asSeller(13L, NOW.minusDays(2), 2),   // +1 -1
                Deal.asSeller(14L, NOW.minusDays(1), 1));  // +1 -2

        assertThat(HammerScore.score(deals, 0, 0)).isEqualTo(50 + 5 + 1 + 0.5 - 1 - 2);
    }

    @Test
    @DisplayName("같은 상대와의 거래는 30일에 한 번만 올려 준다 (나쁜 리뷰는 그래도 깎는다)")
    void samePartnerCountsOncePer30Days() {
        List<Deal> deals = List.of(
                Deal.asSeller(10L, NOW.minusDays(40), 5),  // +2 (반영)
                Deal.asSeller(10L, NOW.minusDays(35), 5),  // 30일 안 → 0
                Deal.asBuyer(10L, NOW.minusDays(20)),      // 반영 뒤 30일 안 → 0 (역할이 바뀌어도 같은 상대)
                Deal.asSeller(10L, NOW.minusDays(15), 1),  // 30일 안 → 나쁜 리뷰 -2만
                Deal.asBuyer(10L, NOW.minusDays(5)));      // 첫 반영 뒤 35일 → +1

        assertThat(HammerScore.score(deals, 0, 0)).isEqualTo(50 + 2 - 2 + 1);
    }

    @Test
    @DisplayName("거래 순서와 상관없이 오래된 거래부터 30일 규칙을 적용한다")
    void appliesPartnerRuleInTimeOrder() {
        List<Deal> deals = List.of(
                Deal.asBuyer(10L, NOW.minusDays(5)),
                Deal.asBuyer(10L, NOW.minusDays(40)));

        assertThat(HammerScore.score(deals, 0, 0)).isEqualTo(52.0);
    }

    @Test
    @DisplayName("배송지 미입력 낙찰 취소와 판매자 미발송 취소는 건마다 -5")
    void responsibleCancelsSubtractFive() {
        assertThat(HammerScore.score(List.of(), 1, 2)).isEqualTo(35.0);
    }

    @Test
    @DisplayName("점수는 0~100 사이로 자른다")
    void clampsScore() {
        List<Deal> many = new ArrayList<>();
        for (long partner = 1; partner <= 40; partner++) {
            many.add(Deal.asSeller(partner, NOW.minusDays(partner), 5));
        }

        assertThat(HammerScore.score(many, 0, 0)).isEqualTo(100.0);
        assertThat(HammerScore.score(List.of(), 6, 6)).isZero();
    }

    @Test
    @DisplayName("구매 확정 거래가 3건 미만이면 점수와 상관없이 새 망치")
    void newbieUnderThreeCompletedDeals() {
        assertThat(HammerScore.grade(90, 2)).isEqualTo(HammerGrade.NEWBIE);
        assertThat(HammerScore.grade(10, 0)).isEqualTo(HammerGrade.NEWBIE);
    }

    @Test
    @DisplayName("단계 구간: 나무 0-29, 돌 30-44, 은 45-59, 금 60-79, 다이아 80-100")
    void gradeBoundaries() {
        assertThat(HammerScore.grade(0, 3)).isEqualTo(HammerGrade.WOOD);
        assertThat(HammerScore.grade(29.5, 3)).isEqualTo(HammerGrade.WOOD);
        assertThat(HammerScore.grade(30, 3)).isEqualTo(HammerGrade.STONE);
        assertThat(HammerScore.grade(44.5, 3)).isEqualTo(HammerGrade.STONE);
        assertThat(HammerScore.grade(45, 3)).isEqualTo(HammerGrade.SILVER);
        assertThat(HammerScore.grade(59.5, 3)).isEqualTo(HammerGrade.SILVER);
        assertThat(HammerScore.grade(60, 3)).isEqualTo(HammerGrade.GOLD);
        assertThat(HammerScore.grade(79.5, 3)).isEqualTo(HammerGrade.GOLD);
        assertThat(HammerScore.grade(80, 3)).isEqualTo(HammerGrade.DIAMOND);
        assertThat(HammerScore.grade(100, 3)).isEqualTo(HammerGrade.DIAMOND);
    }
}
