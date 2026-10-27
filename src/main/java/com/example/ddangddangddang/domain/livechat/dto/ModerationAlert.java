package com.example.ddangddangddang.domain.livechat.dto;

import com.example.ddangddangddang.domain.livechat.moderation.ModerationCategory;

import java.time.LocalDateTime;

/**
 * AI가 확인이 필요하다고 표시한 채팅. 방송 판매자에게만 /user/queue/moderation으로 전달된다.
 * 판매자는 messageId로 채팅 목록의 메시지를 찾아 숨기거나 보낸 사람의 채팅을 금지할 수 있다.
 */
public record ModerationAlert(
        Long liveStreamId,
        String messageId,
        Long senderId,
        String nickname,
        String content,
        ModerationCategory category,
        String categoryLabel,
        String reason,
        LocalDateTime detectedAt
) {
}
