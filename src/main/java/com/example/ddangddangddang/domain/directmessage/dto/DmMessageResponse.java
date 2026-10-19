package com.example.ddangddangddang.domain.directmessage.dto;

import com.example.ddangddangddang.domain.directmessage.entity.DmMessage;

import java.time.LocalDateTime;

/**
 * 메시지 응답. 실시간 전달(/user/queue/dm)에도 같은 형식을 쓴다.
 * 보낸 사람 화면은 clientMessageId로 "보내는 중" 메시지와 서버 저장 결과를 짝지을 수 있다.
 */
public record DmMessageResponse(
        Long messageId,
        Long roomId,
        Long senderId,
        String clientMessageId,
        String content,
        LocalDateTime sentAt
) {
    public static DmMessageResponse from(DmMessage message) {
        return new DmMessageResponse(message.getId(), message.getRoomId(), message.getSenderId(),
                message.getClientMessageId(), message.getContent(), message.getCreatedAt());
    }
}
