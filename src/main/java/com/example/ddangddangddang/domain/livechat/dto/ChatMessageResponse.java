package com.example.ddangddangddang.domain.livechat.dto;

import java.time.LocalDateTime;

/**
 * /topic/live-streams/{liveStreamId}/chat 구독자에게 전송되는 메시지.
 * SYSTEM 메시지는 senderId, nickname이 null이다.
 */
public record ChatMessageResponse(
        ChatMessageType type,
        Long liveStreamId,
        Long senderId,
        String nickname,
        String content,
        LocalDateTime sentAt
) {
    public static ChatMessageResponse chat(Long liveStreamId, Long senderId, String nickname, String content) {
        return new ChatMessageResponse(ChatMessageType.CHAT, liveStreamId, senderId, nickname, content, LocalDateTime.now());
    }

    public static ChatMessageResponse system(Long liveStreamId, String content) {
        return new ChatMessageResponse(ChatMessageType.SYSTEM, liveStreamId, null, null, content, LocalDateTime.now());
    }
}
