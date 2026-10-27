package com.example.ddangddangddang.domain.auction.event;

import com.example.ddangddangddang.domain.auction.dto.response.LiveAuctionChangedMessage;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LiveAuctionEventListener {

    static final String LIVE_AUCTIONS_TOPIC = "/topic/live-streams/%d/auctions";

    private final StompBroadcaster stompBroadcaster;

    /**
     * 커밋된 뒤에만 알린다. 시청 화면은 이 알림을 받으면 방송의 경매 목록(GET /lives/{id}/auctions)을 다시 불러온다.
     */
    @TransactionalEventListener
    public void handleLiveAuction(LiveAuctionEvent event) {
        stompBroadcaster.broadcast(LIVE_AUCTIONS_TOPIC.formatted(event.liveStreamId()),
                LiveAuctionChangedMessage.of(event.auctionId()));
    }
}
