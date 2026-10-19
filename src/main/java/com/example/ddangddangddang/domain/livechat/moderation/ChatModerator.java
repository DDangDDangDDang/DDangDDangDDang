package com.example.ddangddangddang.domain.livechat.moderation;

import java.util.List;

/**
 * 이미 전송된 채팅을 모아서 검토하고, 판매자 확인이 필요한 메시지만 돌려준다.
 * AI 구현(ClaudeChatModerator)을 인터페이스 뒤에 두어 테스트에서 바꾸고, 모델이나 제공사를 바꿔도 채팅 코드는 그대로 둔다.
 */
public interface ChatModerator {

    /** false면 메시지를 모으지도 않는다. (AI 검토를 끈 상태) */
    boolean isEnabled();

    /**
     * 판매자 확인이 필요한 메시지만 반환한다. 문제가 없으면 빈 목록.
     * 결과는 "제안"이다. 메시지를 숨기거나 채팅을 금지하는 결정은 판매자가 한다.
     */
    List<ModerationFlag> review(List<ChatForReview> messages);

    /** 검토 대상 메시지. AI에는 messageId와 content만 보내고 회원 정보는 보내지 않는다. */
    record ChatForReview(String messageId, Long senderId, String nickname, String content) {
    }

    record ModerationFlag(String messageId, ModerationCategory category, String reason) {
    }
}
