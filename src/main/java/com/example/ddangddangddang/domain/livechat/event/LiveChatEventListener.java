package com.example.ddangddangddang.domain.livechat.event;

import com.example.ddangddangddang.domain.auction.event.LiveAuctionEvent;
import com.example.ddangddangddang.domain.livechat.service.LiveChatService;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamEndedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class LiveChatEventListener {

    static final String STREAM_ENDED_MESSAGE = "방송이 종료되었습니다.";
    private static final DateTimeFormatter START_AT_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final LiveChatService liveChatService;

    /**
     * 종료가 커밋된 뒤에만 안내한다. 종료 처리가 실패해 롤백되면 안내도 나가지 않는다.
     * 이후 보내는 채팅은 방송 상태 확인에서 막힌다.
     */
    @TransactionalEventListener
    public void handleLiveStreamEnded(LiveStreamEndedEvent event) {
        liveChatService.sendSystemMessage(event.liveStreamId(), STREAM_ENDED_MESSAGE);
    }

    /**
     * 방송에 올라간 경매가 시작되거나 새로 올라오면 채팅에 안내한다. (취소·마감 등은 안내하지 않음)
     */
    @TransactionalEventListener
    public void handleLiveAuction(LiveAuctionEvent event) {
        String message = liveAuctionMessage(event, LocalDateTime.now());
        if (message != null) {
            liveChatService.sendSystemMessage(event.liveStreamId(), message);
        }
    }

    static String liveAuctionMessage(LiveAuctionEvent event, LocalDateTime now) {
        String price = "%,dP".formatted(event.currentPrice());
        return switch (event.type()) {
            case STARTED -> "‘%s’ 경매가 시작되었습니다. 시작가 %s".formatted(event.productName(), price);
            case SHOWN -> event.startAt().isAfter(now)
                    ? "‘%s’ 경매가 방송에 올라왔습니다. %s에 시작합니다.".formatted(event.productName(), event.startAt().format(START_AT_FORMAT))
                    : "‘%s’ 경매가 방송에 올라왔습니다. 현재가 %s".formatted(event.productName(), price);
            case CHANGED -> null;
        };
    }
}
