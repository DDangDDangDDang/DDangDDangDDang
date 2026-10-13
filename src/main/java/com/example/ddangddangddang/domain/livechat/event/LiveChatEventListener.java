package com.example.ddangddangddang.domain.livechat.event;

import com.example.ddangddangddang.domain.livechat.service.LiveChatService;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamEndedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LiveChatEventListener {

    static final String STREAM_ENDED_MESSAGE = "방송이 종료되었습니다.";

    private final LiveChatService liveChatService;

    /**
     * 종료가 커밋된 뒤에만 안내한다. 종료 처리가 실패해 롤백되면 안내도 나가지 않는다.
     * 이후 보내는 채팅은 방송 상태 확인에서 막힌다.
     */
    @TransactionalEventListener
    public void handleLiveStreamEnded(LiveStreamEndedEvent event) {
        liveChatService.sendSystemMessage(event.liveStreamId(), STREAM_ENDED_MESSAGE);
    }
}
