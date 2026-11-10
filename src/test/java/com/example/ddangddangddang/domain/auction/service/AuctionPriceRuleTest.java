package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 경매 생성 가격 규칙 (AuctionService.priceVerification) */
class AuctionPriceRuleTest {

    private static void assertError(Executable executable, AuctionErrorCode code) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    @Test
    @DisplayName("시작가·입찰 단위를 다 넣으면 입찰 경매, 즉시구매가는 선택이고 시작가보다 커야 한다")
    void bidding() {
        assertThatCode(() -> AuctionService.priceVerification(10_000L, 1_000L, null)).doesNotThrowAnyException();
        assertThatCode(() -> AuctionService.priceVerification(10_000L, 1_000L, 50_000L)).doesNotThrowAnyException();
        assertError(() -> AuctionService.priceVerification(10_000L, 1_000L, 10_000L), AuctionErrorCode.INVALID_BUY_NOW_PRICE);
    }

    @Test
    @DisplayName("시작가·입찰 단위를 둘 다 비우면 즉시구매 전용이고 즉시구매가가 필요하다")
    void buyNowOnly() {
        assertThatCode(() -> AuctionService.priceVerification(null, null, 50_000L)).doesNotThrowAnyException();
        assertError(() -> AuctionService.priceVerification(null, null, null), AuctionErrorCode.BUY_NOW_PRICE_REQUIRED);
    }

    @Test
    @DisplayName("시작가·입찰 단위·즉시구매가는 1억 원까지")
    void maxPrice() {
        long max = com.example.ddangddangddang.domain.auction.entity.Auction.MAX_PRICE;
        assertThatCode(() -> AuctionService.priceVerification(max - 1, 1L, max)).doesNotThrowAnyException();
        assertError(() -> AuctionService.priceVerification(max + 1, 1_000L, null), AuctionErrorCode.PRICE_TOO_HIGH);
        assertError(() -> AuctionService.priceVerification(1_000L, Long.MAX_VALUE, null), AuctionErrorCode.PRICE_TOO_HIGH);
        assertError(() -> AuctionService.priceVerification(null, null, max + 1), AuctionErrorCode.PRICE_TOO_HIGH);
    }

    @Test
    @DisplayName("일반 경매는 1분~14일 진행하고, 시작은 30일 이내다")
    void schedule() {
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 11, 4, 12, 0);
        java.time.LocalDateTime start = now.plusHours(1);
        assertThatCode(() -> AuctionService.scheduleVerification(start, start.plusMinutes(1), now)).doesNotThrowAnyException();
        assertThatCode(() -> AuctionService.scheduleVerification(start, start.plusDays(14), now)).doesNotThrowAnyException();
        assertError(() -> AuctionService.scheduleVerification(start, start, now), AuctionErrorCode.INVALID_TIME);
        assertError(() -> AuctionService.scheduleVerification(start, start.plusSeconds(59), now), AuctionErrorCode.INVALID_DURATION);
        assertError(() -> AuctionService.scheduleVerification(start, start.plusDays(14).plusSeconds(1), now), AuctionErrorCode.INVALID_DURATION);
        assertError(() -> AuctionService.scheduleVerification(now.plusDays(31), now.plusDays(32), now), AuctionErrorCode.START_TOO_FAR);
    }

    @Test
    @DisplayName("시작가·입찰 단위 중 하나만 비우면 오류")
    void onlyOne() {
        assertError(() -> AuctionService.priceVerification(10_000L, null, 50_000L), AuctionErrorCode.INVALID_PRICE_SETTING);
        assertError(() -> AuctionService.priceVerification(null, 1_000L, 50_000L), AuctionErrorCode.INVALID_PRICE_SETTING);
    }
}
