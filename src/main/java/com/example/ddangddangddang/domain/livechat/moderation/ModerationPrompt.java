package com.example.ddangddangddang.domain.livechat.moderation;

import com.example.ddangddangddang.domain.livechat.moderation.ChatModerator.ChatForReview;

import java.util.List;

/**
 * AI에 보낼 채팅 목록을 만든다.
 * 채팅은 사용자가 쓴 글이라 신뢰하지 않는다. <chat_messages> 태그 안에 데이터로만 넣고,
 * 채팅에 "</chat_messages>" 같은 글자를 써서 태그 밖으로 빠져나오지 못하게 꺾쇠를 이스케이프한다.
 * 회원 ID와 닉네임은 보내지 않는다. (검토에 필요 없는 개인정보)
 */
final class ModerationPrompt {

    private ModerationPrompt() {
    }

    static String userMessage(List<ChatForReview> messages) {
        StringBuilder sb = new StringBuilder("다음 채팅을 검토해 주세요.\n<chat_messages>\n");
        for (ChatForReview message : messages) {
            sb.append("{\"messageId\":\"").append(escape(message.messageId()))
                    .append("\",\"content\":\"").append(escape(message.content()))
                    .append("\"}\n");
        }
        return sb.append("</chat_messages>").toString();
    }

    // JSON 문자열 이스케이프 + 꺾쇠 이스케이프
    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
