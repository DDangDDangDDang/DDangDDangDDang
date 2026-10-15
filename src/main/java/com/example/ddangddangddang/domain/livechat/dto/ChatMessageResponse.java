package com.example.ddangddangddang.domain.livechat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * /topic/live-streams/{liveStreamId}/chat 구독자에게 전송되는 메시지.
 * SYSTEM 메시지는 senderId, nickname이 null이다.
 * messageId는 메시지를 가리킬 때 쓴다. (판매자 검토 알림 → 해당 메시지 찾기)
 */
public record ChatMessageResponse(
        String messageId,
        ChatMessageType type,
        Long liveStreamId,
        Long senderId,
        String nickname,
        String content,
        LocalDateTime sentAt
) {
    public static ChatMessageResponse chat(Long liveStreamId, Long senderId, String nickname, String content) {
        return new ChatMessageResponse(newId(), ChatMessageType.CHAT, liveStreamId, senderId, nickname, content, LocalDateTime.now());
    }

    public static ChatMessageResponse system(Long liveStreamId, String content) {
        return new ChatMessageResponse(newId(), ChatMessageType.SYSTEM, liveStreamId, null, null, content, LocalDateTime.now());
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
