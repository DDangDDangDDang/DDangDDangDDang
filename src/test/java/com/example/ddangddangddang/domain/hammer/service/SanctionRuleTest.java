package com.example.ddangddangddang.domain.hammer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SanctionRuleTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Test
    @DisplayName("책임 취소가 없거나 1회면 정지하지 않는다 (경고만)")
    void noBanUnderTwo() {
        assertThat(SanctionRule.of(List.of())).isNull();
        assertThat(SanctionRule.of(List.of(T))).isNull();
    }

    @Test
    @DisplayName("90일 안에 2회면 마지막 취소부터 7일 정지")
    void twoCancelsBanSevenDays() {
        SanctionRule.Result result = SanctionRule.of(List.of(T.minusDays(30), T));

        assertThat(result.cancelCount()).isEqualTo(2);
        assertThat(result.bannedUntil()).isEqualTo(T.plusDays(7));
        assertThat(result.needsReview()).isFalse();
    }

    @Test
    @DisplayName("90일 안에 3회면 마지막 취소부터 30일 정지")
    void threeCancelsBanThirtyDays() {
        SanctionRule.Result result = SanctionRule.of(List.of(T, T.minusDays(60), T.minusDays(10)));

        assertThat(result.cancelCount()).isEqualTo(3);
        assertThat(result.bannedUntil()).isEqualTo(T.plusDays(30));
        assertThat(result.needsReview()).isFalse();
    }

    @Test
    @DisplayName("90일 안에 4회 이상이면 30일 정지에 관리자 검토 대상")
    void fourCancelsNeedReview() {
        SanctionRule.Result result = SanctionRule.of(List.of(T.minusDays(3), T.minusDays(2), T.minusDays(1), T));

        assertThat(result.cancelCount()).isEqualTo(4);
        assertThat(result.bannedUntil()).isEqualTo(T.plusDays(30));
        assertThat(result.needsReview()).isTrue();
    }

    @Test
    @DisplayName("마지막 취소 기준 90일보다 오래된 취소는 세지 않는다")
    void countsOnlyNinetyDaysBeforeLatest() {
        assertThat(SanctionRule.of(List.of(T.minusDays(90), T))).isNull();
        assertThat(SanctionRule.of(List.of(T.minusDays(89), T)).cancelCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("정지 기간은 시간이 지나도 줄지 않는다: 첫 취소가 90일 밖으로 나가도 마지막 취소 기준으로 센다")
    void banDoesNotShrinkOverTime() {
        // 3회 → 30일 정지. 기준이 '지금'이 아니라 마지막 취소라서 결과가 늘 같다
        SanctionRule.Result result = SanctionRule.of(List.of(T.minusDays(85), T.minusDays(40), T));

        assertThat(result.bannedUntil()).isEqualTo(T.plusDays(30));
    }
}
