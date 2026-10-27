package com.example.ddangddangddang.domain.auction.entity;

import com.example.ddangddangddang.domain.auction.AuctionFixture;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.example.ddangddangddang.domain.auction.AuctionFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionBidRuleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 20, 0);
    private static final Long BIDDER = 2L;
    private static final Long OTHER = 3L;

    @Nested
    @DisplayName("입찰 검증")
    class ValidateBid {

        @Test
        @DisplayName("첫 입찰은 시작가부터 가능하다")
        void firstBidFromStartPrice() {
            Auction auction = AuctionFixture.ongoing(NOW);

            assertThat(auction.minimumBidAmount()).isEqualTo(10_000L);
            assertThatCode(() -> auction.validateBid(BIDDER, 10_000L, NOW)).doesNotThrowAnyException();
            assertError(() -> auction.validateBid(BIDDER, 9_999L, NOW), AuctionErrorCode.BID_TOO_LOW);
        }

        @Test
        @DisplayName("이후 입찰은 현재가 + 입찰 단위 이상이어야 한다")
        void nextBid() {
            Auction auction = AuctionFixture.ongoing(NOW);
            AuctionFixture.bidBy(auction, OTHER, 20_000L);

            assertThat(auction.minimumBidAmount()).isEqualTo(21_000L);
            assertError(() -> auction.validateBid(BIDDER, 20_999L, NOW), AuctionErrorCode.BID_TOO_LOW);
            assertThatCode(() -> auction.validateBid(BIDDER, 21_000L, NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("판매자는 입찰할 수 없다")
        void seller() {
            Auction auction = AuctionFixture.ongoing(NOW);
            assertError(() -> auction.validateBid(SELLER_ID, 10_000L, NOW), AuctionErrorCode.SELLER_CANNOT_BID);
        }

        @Test
        @DisplayName("이미 최고 입찰자면 다시 입찰할 수 없다")
        void alreadyHighest() {
            Auction auction = AuctionFixture.ongoing(NOW);
            AuctionFixture.bidBy(auction, BIDDER, 20_000L);
            assertError(() -> auction.validateBid(BIDDER, 30_000L, NOW), AuctionErrorCode.ALREADY_HIGHEST_BIDDER);
        }

        @Test
        @DisplayName("즉시구매가 이상으로는 입찰할 수 없다")
        void reachesBuyNow() {
            Auction auction = AuctionFixture.ongoing(NOW);
            assertError(() -> auction.validateBid(BIDDER, 100_000L, NOW), AuctionErrorCode.BID_REACHES_BUY_NOW);
            assertThatCode(() -> auction.validateBid(BIDDER, 99_000L, NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("즉시구매가가 없으면 금액 상한이 없다")
        void noBuyNow() {
            Auction auction = AuctionFixture.ongoing(NOW, Duration.ofHours(1), null);
            assertThatCode(() -> auction.validateBid(BIDDER, 10_000_000L, NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("입찰 금액은 기준가(첫 입찰은 시작가, 이후는 현재가)에서 입찰 단위만큼씩 올려야 한다")
        void bidUnit() {
            Auction auction = AuctionFixture.ongoing(NOW); // 시작가 10,000 · 단위 1,000
            assertError(() -> auction.validateBid(BIDDER, 10_500L, NOW), AuctionErrorCode.BID_UNIT_MISMATCH);
            assertThatCode(() -> auction.validateBid(BIDDER, 12_000L, NOW)).doesNotThrowAnyException();

            AuctionFixture.bidBy(auction, OTHER, 20_000L);
            assertError(() -> auction.validateBid(BIDDER, 21_500L, NOW), AuctionErrorCode.BID_UNIT_MISMATCH);
            assertThatCode(() -> auction.validateBid(BIDDER, 40_000L, NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("단위가 맞지 않는 예전 입찰이 현재가여도 그 현재가를 기준으로 계속 입찰할 수 있다")
        void bidUnitFromCurrentPrice() {
            Auction auction = AuctionFixture.ongoing(NOW);
            AuctionFixture.bidBy(auction, OTHER, 15_500L); // 규칙 도입 전에 들어간 입찰

            assertThatCode(() -> auction.validateBid(BIDDER, 16_500L, NOW)).doesNotThrowAnyException();
            assertError(() -> auction.validateBid(BIDDER, 17_000L, NOW), AuctionErrorCode.BID_UNIT_MISMATCH);
        }

        @Test
        @DisplayName("최소 금액보다 낮으면 단위보다 금액 부족을 먼저 알린다")
        void tooLowBeforeUnit() {
            Auction auction = AuctionFixture.ongoing(NOW);
            assertError(() -> auction.validateBid(BIDDER, 9_500L, NOW), AuctionErrorCode.BID_TOO_LOW);
        }

        @Test
        @DisplayName("마감 시각이 지났거나 진행 중이 아니면 입찰할 수 없다")
        void closed() {
            Auction auction = AuctionFixture.ongoing(NOW, Duration.ofSeconds(10), null);
            assertError(() -> auction.validateBid(BIDDER, 10_000L, NOW.plusSeconds(10)), AuctionErrorCode.AUCTION_TIME_OVER);

            auction.cancel();
            assertError(() -> auction.validateBid(BIDDER, 10_000L, NOW), AuctionErrorCode.AUCTION_NOT_ONGOING);
        }
    }

    @Nested
    @DisplayName("마감 재시도")
    class CloseRetry {

        @Test
        @DisplayName("실패할 때마다 다음 시도까지 1초, 2초, 4초 ... 로 늘리고 최대 5분까지만 기다린다")
        void backoff() {
            Auction auction = AuctionFixture.ongoing(NOW);

            auction.recordCloseFailure(NOW);
            assertThat(auction.getNextCloseAttemptAt()).isEqualTo(NOW.plusSeconds(1));
            auction.recordCloseFailure(NOW);
            assertThat(auction.getNextCloseAttemptAt()).isEqualTo(NOW.plusSeconds(2));
            auction.recordCloseFailure(NOW);
            assertThat(auction.getNextCloseAttemptAt()).isEqualTo(NOW.plusSeconds(4));

            for (int i = 0; i < 5; i++) {
                auction.recordCloseFailure(NOW);
            }
            assertThat(auction.getNextCloseAttemptAt()).isEqualTo(NOW.plusSeconds(128));
            auction.recordCloseFailure(NOW);
            assertThat(auction.getNextCloseAttemptAt()).isEqualTo(NOW.plusSeconds(256));
        }

        @Test
        @DisplayName("한도만큼 실패하면 더 시도하지 않는다고 알린다")
        void exhausted() {
            Auction auction = AuctionFixture.ongoing(NOW);

            for (int i = 1; i < Auction.MAX_CLOSE_FAILURES; i++) {
                assertThat(auction.recordCloseFailure(NOW)).isFalse();
            }
            assertThat(auction.recordCloseFailure(NOW)).isTrue();
            assertThat(auction.getCloseFailCount()).isEqualTo(Auction.MAX_CLOSE_FAILURES);
        }
    }

    @Nested
    @DisplayName("스나이핑 방지 연장")
    class Extension {

        @Test
        @DisplayName("마감 30초 전보다 이르면 연장하지 않는다")
        void notExtended() {
            Auction auction = AuctionFixture.ongoing(NOW, Duration.ofSeconds(31), null);
            assertThat(auction.endAtAfterBid(NOW)).isEqualTo(auction.getEndAt());
        }

        @Test
        @DisplayName("마감 30초 전 이내면 남은 시간이 다시 30초가 되게 미룬다 (입찰 시각 + 30초)")
        void extended() {
            Auction auction = AuctionFixture.ongoing(NOW, Duration.ofSeconds(10), null);
            assertThat(auction.endAtAfterBid(NOW)).isEqualTo(NOW.plusSeconds(30));
        }

        @Test
        @DisplayName("정확히 30초 남았을 때는 그대로다 (30초를 넘게 늘리지 않는다)")
        void notBeyondThirtySeconds() {
            Auction auction = AuctionFixture.ongoing(NOW, Duration.ofSeconds(30), null);
            assertThat(auction.endAtAfterBid(NOW)).isEqualTo(auction.getEndAt());
        }

        @Test
        @DisplayName("값만 계산하고 엔티티는 바꾸지 않는다")
        void pure() {
            Auction auction = AuctionFixture.ongoing(NOW, Duration.ofSeconds(5), null);
            LocalDateTime before = auction.getEndAt();
            auction.endAtAfterBid(NOW);
            assertThat(auction.getEndAt()).isEqualTo(before);
            assertThat(auction.getExtendCount()).isZero();
        }
    }

    @Nested
    @DisplayName("즉시구매")
    class BuyNow {

        @Test
        @DisplayName("즉시구매하면 구매자가 낙찰자, 즉시구매가가 최종가가 되고 종료된다")
        void buyNow() {
            Auction auction = AuctionFixture.ongoing(NOW);
            AuctionFixture.bidBy(auction, OTHER, 50_000L);

            auction.validateBuyNow(BIDDER, NOW);
            auction.buyNow(BIDDER, NOW);

            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDED);
            assertThat(auction.getHighestBidderId()).isEqualTo(BIDDER);
            assertThat(auction.getCurrentPrice()).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("즉시구매가가 없거나 판매자면 구매할 수 없다")
        void notAvailable() {
            Auction noBuyNow = AuctionFixture.ongoing(NOW, Duration.ofHours(1), null);
            assertError(() -> noBuyNow.validateBuyNow(BIDDER, NOW), AuctionErrorCode.BUY_NOW_NOT_AVAILABLE);

            Auction auction = AuctionFixture.ongoing(NOW);
            assertError(() -> auction.validateBuyNow(SELLER_ID, NOW), AuctionErrorCode.SELLER_CANNOT_BID);
        }
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, AuctionErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }
}
