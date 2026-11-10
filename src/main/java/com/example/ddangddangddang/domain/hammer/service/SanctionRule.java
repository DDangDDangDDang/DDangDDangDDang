package com.example.ddangddangddang.domain.hammer.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 본인 책임 취소 횟수로 정하는 정지 규칙. DB를 모르는 순수 계산이다.
 *
 * - 마지막 취소 기준 90일 안의 책임 취소 수를 센다. (기준이 '지금'이 아니라서 정지 기간이 시간이 지나며 줄지 않는다)
 * - 1회: 경고만, 2회: 마지막 취소부터 7일 정지, 3회 이상: 30일 정지, 4회 이상: 관리자 검토 대상
 * 어느 취소부터 셀지(시행일, 관리자 해제 뒤)는 부르는 쪽이 걸러서 넘긴다.
 */
public final class SanctionRule {

    static final int WINDOW_DAYS = 90;
    static final int SHORT_BAN_DAYS = 7;
    static final int LONG_BAN_DAYS = 30;
    static final int REVIEW_COUNT = 4;

    private SanctionRule() {
    }

    public record Result(int cancelCount, LocalDateTime bannedUntil, boolean needsReview) {

        public boolean isActive(LocalDateTime now) {
            return bannedUntil.isAfter(now);
        }
    }

    /** 정지할 일이 없으면 (취소 0~1회) null */
    public static Result of(List<LocalDateTime> cancelTimes) {
        if (cancelTimes.isEmpty()) {
            return null;
        }
        LocalDateTime latest = cancelTimes.stream().max(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime windowStart = latest.minusDays(WINDOW_DAYS);
        int count = (int) cancelTimes.stream().filter(time -> time.isAfter(windowStart)).count();
        if (count < 2) {
            return null;
        }
        int banDays = count == 2 ? SHORT_BAN_DAYS : LONG_BAN_DAYS;
        return new Result(count, latest.plusDays(banDays), count >= REVIEW_COUNT);
    }
}
