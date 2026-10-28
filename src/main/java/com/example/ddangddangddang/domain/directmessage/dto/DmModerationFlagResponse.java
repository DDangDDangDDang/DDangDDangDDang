package com.example.ddangddangddang.domain.directmessage.dto;

import com.example.ddangddangddang.domain.directmessage.entity.DmModerationFlag;
import com.example.ddangddangddang.domain.livechat.moderation.ModerationCategory;

import java.time.LocalDateTime;

/**
 * AI가 관리자 확인이 필요하다고 표시한 1:1 메시지. content는 표시된 메시지 내용이다. (메시지를 찾지 못하면 null)
 */
public record DmModerationFlagResponse(
        Long flagId,
        Long roomId,
        Long messageId,
        Long senderId,
        ModerationCategory category,
        String categoryLabel,
        String reason,
        String content,
        LocalDateTime flaggedAt
) {
    public static DmModerationFlagResponse of(DmModerationFlag flag, String content) {
        return new DmModerationFlagResponse(flag.getId(), flag.getRoomId(), flag.getMessageId(), flag.getSenderId(),
                flag.getCategory(), flag.getCategory().getLabel(), flag.getReason(), content, flag.getCreatedAt());
    }
}
