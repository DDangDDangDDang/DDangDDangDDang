package com.example.ddangddangddang.domain.livechat.moderation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 검토를 끈 상태(chat.moderation.ai.enabled=false, 기본값). 전송 전 규칙 검사(ChatContentFilter)만 동작한다.
 */
@Component
@ConditionalOnProperty(name = "chat.moderation.ai.enabled", havingValue = "false", matchIfMissing = true)
public class NoopChatModerator implements ChatModerator {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public List<ModerationFlag> review(ReviewChannel channel, List<ChatForReview> messages) {
        return List.of();
    }
}
