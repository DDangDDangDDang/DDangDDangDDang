package com.example.ddangddangddang.domain.livechat.service;

import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LiveChatService {

    private static final String CHAT_TOPIC = "/topic/live-streams/%d/chat";

    private final SimpMessagingTemplate messagingTemplate;

    // TODO(2단계): 방송 상태 확인, 도배 방지, 금칙어
    public void send(Long liveStreamId, AuthUser sender, String content) {
        broadcast(liveStreamId, ChatMessageResponse.chat(liveStreamId, sender.id(), sender.nickname(), content.strip()));
    }

    private void broadcast(Long liveStreamId, ChatMessageResponse message) {
        messagingTemplate.convertAndSend(CHAT_TOPIC.formatted(liveStreamId), message);
    }
}
