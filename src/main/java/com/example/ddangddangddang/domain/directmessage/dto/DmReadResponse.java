package com.example.ddangddangddang.domain.directmessage.dto;

/**
 * 읽음 처리 결과. lastReadMessageId는 처리 후 내 읽음 위치, unreadCount는 이 방에 남은 읽지 않은 메시지 수.
 */
public record DmReadResponse(Long roomId, Long lastReadMessageId, long unreadCount) {
}
