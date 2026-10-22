package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.AuctionFixture;
import com.example.ddangddangddang.domain.auction.dto.response.BidResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.entity.Bid;
import com.example.ddangddangddang.domain.auction.event.BidPlacedEvent;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.repository.BidRepository;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.ddangddangddang.domain.auction.AuctionFixture.AUCTION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Long BIDDER = 2L;
    private static final Long PREV_BIDDER = 3L;

    @InjectMocks
    private BidService bidService;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private PointService pointService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("첫 입찰이 반영되면 새 입찰자만 홀딩한다")
    void firstBid() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
        givenUpdateResult(1);
        given(bidRepository.save(any(Bid.class))).willAnswer(invocation -> invocation.getArgument(0));

        Optional<BidResponse> result = bidService.tryPlaceBid(BIDDER, AUCTION_ID, 10_000L);

        assertThat(result).isPresent();
        assertThat(result.get().bidCount()).isEqualTo(1);
        verify(pointService).hold(BIDDER, 10_000L, AUCTION_ID);
        verify(pointService, never()).replaceHold(any(), anyLong(), any(), anyLong(), any());
        verify(eventPublisher).publishEvent(any(BidPlacedEvent.class));
    }

    @Test
    @DisplayName("상위 입찰이 반영되면 조회했던 이전 최고 입찰자와 금액으로 홀딩을 교체한다")
    void outbid() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        AuctionFixture.bidBy(auction, PREV_BIDDER, 20_000L);
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
        givenUpdateResult(1);
        given(bidRepository.save(any(Bid.class))).willAnswer(invocation -> invocation.getArgument(0));

        bidService.tryPlaceBid(BIDDER, AUCTION_ID, 21_000L);

        verify(pointService).replaceHold(BIDDER, 21_000L, PREV_BIDDER, 20_000L, AUCTION_ID);
    }

    @Test
    @DisplayName("조건부 UPDATE가 0행이면(다른 입찰이 먼저 반영) 포인트를 건드리지 않고 빈 값을 돌려준다")
    void conflict() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
        givenUpdateResult(0);

        Optional<BidResponse> result = bidService.tryPlaceBid(BIDDER, AUCTION_ID, 10_000L);

        assertThat(result).isEmpty();
        verify(pointService, never()).hold(any(), anyLong(), any());
        verify(bidRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("입찰 규칙에 맞지 않으면 UPDATE를 시도하지 않는다")
    void invalid() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));

        assertThatThrownBy(() -> bidService.tryPlaceBid(BIDDER, AUCTION_ID, 9_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.BID_TOO_LOW);
        verify(auctionRepository, never()).placeBid(any(), any(), anyLong(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("UPDATE에는 조회했던 version을 조건으로 넘긴다")
    void passesSeenVersion() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(auction, "version", 7L);
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
        given(auctionRepository.placeBid(eq(AUCTION_ID), eq(BIDDER), eq(10_000L), any(), anyInt(),
                eq(7L), eq(AuctionStatus.ONGOING), any())).willReturn(0);

        assertThat(bidService.tryPlaceBid(BIDDER, AUCTION_ID, 10_000L)).isEmpty();
    }

    private void givenUpdateResult(int result) {
        given(auctionRepository.placeBid(any(), any(), anyLong(), any(), anyInt(), any(), any(), any()))
                .willReturn(result);
    }
}
