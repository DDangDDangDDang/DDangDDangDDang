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
            assertThatCode(() -> auction.validateBid(BIDDER, 99_999L, NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("즉시구매가가 없으면 금액 상한이 없다")
        void noBuyNow() {
            Auction auction = AuctionFixture.ongoing(NOW, Duration.ofHours(1), null);
            assertThatCode(() -> auction.validateBid(BIDDER, 10_000_000L, NOW)).doesNotThrowAnyException();
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
    @DisplayName("스나이핑 방지 연장")
    class Extension {

        @Test
        @DisplayName("마감 30초 전보다 이르면 연장하지 않는다")
        void notExtended() {
            Auction auction = AuctionFixture.ongoing(NOW, Duration.ofSeconds(31), null);
            assertThat(auction.endAtAfterBid(NOW)).isEqualTo(auction.getEndAt());
        }

        @Test
        @DisplayName("마감 30초 전 이내면 30초 연장한다")
        void extended() {
            Auction auction = AuctionFixture.ongoing(NOW, Duration.ofSeconds(30), null);
            assertThat(auction.endAtAfterBid(NOW)).isEqualTo(auction.getEndAt().plusSeconds(30));
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
