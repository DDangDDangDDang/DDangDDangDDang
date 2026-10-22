package com.example.ddangddangddang.domain.auction.event;

import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 입찰·종료를 경매 구독자에게 알린다. 커밋된 뒤에만 보낸다. 롤백되면 알림도 나가지 않는다.
 * 프론트는 /topic/auctions/{auctionId} 를 구독한다.
 *
 * 알림 실패(Redis 장애 등)는 로그만 남긴다. 이미 커밋된 입찰을 사용자에게 실패처럼 보이게 하지 않기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventListener {

    static final String AUCTION_TOPIC = "/topic/auctions/%d";

    private final StompBroadcaster stompBroadcaster;

    @TransactionalEventListener
    public void handleBidPlaced(BidPlacedEvent event) {
        broadcast(event.auctionId(), event);
    }

    @TransactionalEventListener
    public void handleAuctionEnded(AuctionEndedEvent event) {
        broadcast(event.auctionId(), event);
    }

    private void broadcast(Long auctionId, Object payload) {
        try {
            stompBroadcaster.broadcast(AUCTION_TOPIC.formatted(auctionId), payload);
        } catch (Exception e) {
            log.warn("경매 알림 전송 실패 auctionId={}", auctionId, e);
        }
    }
}
