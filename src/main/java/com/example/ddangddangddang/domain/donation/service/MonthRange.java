package com.example.ddangddangddang.domain.donation.service;

import java.time.LocalDateTime;
import java.time.YearMonth;

/** 한 달(1일 0시 ~ 다음 달 1일 0시 전). 적립·선물 한도는 달력의 달마다 새로 센다. */
record MonthRange(LocalDateTime from, LocalDateTime to) {

    static MonthRange of(LocalDateTime now) {
        YearMonth month = YearMonth.from(now);
        return new MonthRange(month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }
}
