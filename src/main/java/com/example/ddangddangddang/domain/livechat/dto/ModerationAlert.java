package com.example.ddangddangddang.domain.livechat.dto;

import com.example.ddangddangddang.domain.livechat.moderation.ModerationCategory;

import java.time.LocalDateTime;

/**
 * AI가 확인이 필요하다고 표시한 채팅·선물 한마디. 방송 판매자에게만 /user/queue/moderation으로 전달된다.
 * messageType은 CHAT(채팅) 또는 DONATION(선물 한마디).
 * autoHidden이 true면 방송 설정(자동 가림)에 따라 이미 가려진 메시지다. 판매자는 잘못 가려졌으면 되돌릴 수 있다.
 */
public record ModerationAlert(
        Long liveStreamId,
        String messageId,
        ChatMessageType messageType,
        Long senderId,
        String nickname,
        String content,
        ModerationCategory category,
        String categoryLabel,
        String reason,
        boolean autoHidden,
        LocalDateTime detectedAt
) {
}
