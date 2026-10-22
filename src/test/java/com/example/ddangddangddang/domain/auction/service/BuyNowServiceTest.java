package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.AuctionFixture;
import com.example.ddangddangddang.domain.auction.dto.response.BuyNowResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.event.AuctionEndedEvent;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
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
import static com.example.ddangddangddang.domain.auction.AuctionFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BuyNowServiceTest {

    private static final Long BUYER = 2L;
    private static final Long PREV_BIDDER = 3L;

    @InjectMocks
    private BuyNowService buyNowService;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private PointService pointService;

    @Mock
    private AuctionSettlementService auctionSettlementService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("입찰자가 없으면 구매자 포인트만 차감하고 주문을 만든다")
    void noBidder() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        given(auctionRepository.findByIdForUpdate(AUCTION_ID)).willReturn(Optional.of(auction));
        given(auctionSettlementService.completeSale(auction)).willReturn(500L);

        BuyNowResponse response = buyNowService.buyNow(BUYER, AUCTION_ID);

        assertThat(response.orderId()).isEqualTo(500L);
        assertThat(response.price()).isEqualTo(100_000L);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDED);
        verify(pointService).purchase(BUYER, 100_000L, AUCTION_ID);
        verify(pointService, never()).releaseAndPurchase(any(), anyLong(), any(), anyLong(), any());
        verify(eventPublisher).publishEvent(any(AuctionEndedEvent.class));
    }

    @Test
    @DisplayName("최고 입찰자가 있으면 그 홀딩을 풀고 구매자를 차감한다 (정렬 잠금 메서드 사용)")
    void withBidder() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        AuctionFixture.bidBy(auction, PREV_BIDDER, 50_000L);
        given(auctionRepository.findByIdForUpdate(AUCTION_ID)).willReturn(Optional.of(auction));

        buyNowService.buyNow(BUYER, AUCTION_ID);

        verify(pointService).releaseAndPurchase(PREV_BIDDER, 50_000L, BUYER, 100_000L, AUCTION_ID);
        verify(pointService, never()).purchase(any(), anyLong(), any());
        assertThat(auction.getHighestBidderId()).isEqualTo(BUYER);
    }

    @Test
    @DisplayName("판매자는 즉시구매할 수 없고 포인트도 건드리지 않는다")
    void seller() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        given(auctionRepository.findByIdForUpdate(AUCTION_ID)).willReturn(Optional.of(auction));

        assertThatThrownBy(() -> buyNowService.buyNow(SELLER_ID, AUCTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.SELLER_CANNOT_BID);
        verify(pointService, never()).purchase(any(), anyLong(), any());
        verify(auctionSettlementService, never()).completeSale(any());
    }
}
