package com.example.ddangddangddang.domain.livestream.event;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamUpdatedMessage;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LiveStreamEventListener {

    static final String LIVE_STREAM_TOPIC = "/topic/live-streams/%d";

    private final StompBroadcaster stompBroadcaster;

    /**
     * 수정이 커밋된 뒤에만 알린다. 롤백되면 알림도 나가지 않는다.
     * 서버가 여러 대여도 StompBroadcaster가 모든 서버의 구독자에게 전달한다.
     */
    @TransactionalEventListener
    public void handleLiveStreamUpdated(LiveStreamUpdatedEvent event) {
        stompBroadcaster.broadcast(LIVE_STREAM_TOPIC.formatted(event.liveStream().id()),
                LiveStreamUpdatedMessage.of(event.liveStream()));
    }
}
