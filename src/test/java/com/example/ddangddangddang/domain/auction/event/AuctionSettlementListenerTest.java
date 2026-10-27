package com.example.ddangddangddang.domain.auction.event;

import com.example.ddangddangddang.domain.auction.AuctionFixture;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.service.AuctionSettlementService;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.ddangddangddang.domain.auction.AuctionFixture.AUCTION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionSettlementListenerTest {

    @InjectMocks
    private AuctionSettlementListener listener;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AuctionSettlementService auctionSettlementService;

    @Test
    @DisplayName("낙찰(ENDED)로 마감되면 정산한다")
    void settleOnEnded() {
        Auction auction = endedWithWinner();
        given(orderRepository.existsByAuctionId(AUCTION_ID)).willReturn(false);
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));

        listener.settleWinningBid(AuctionEndedEvent.from(auction));

        verify(auctionSettlementService).settleWinningBid(auction);
    }

    @Test
    @DisplayName("유찰(FAILED)이면 정산하지 않는다")
    void skipOnFailed() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        auction.close(); // 입찰 0건 → FAILED

        listener.settleWinningBid(AuctionEndedEvent.from(auction));

        verify(orderRepository, never()).existsByAuctionId(anyLong());
        verify(auctionSettlementService, never()).settleWinningBid(any());
    }

    @Test
    @DisplayName("즉시구매처럼 이미 주문이 있으면 다시 정산하지 않는다")
    void skipWhenAlreadySettled() {
        Auction auction = endedWithWinner();
        given(orderRepository.existsByAuctionId(AUCTION_ID)).willReturn(true);

        listener.settleWinningBid(AuctionEndedEvent.from(auction));

        verify(auctionSettlementService, never()).settleWinningBid(any());
    }

    private Auction endedWithWinner() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        AuctionFixture.bidBy(auction, 2L, 30_000L);
        auction.close();
        assert auction.getStatus() == AuctionStatus.ENDED;
        return auction;
    }
}
