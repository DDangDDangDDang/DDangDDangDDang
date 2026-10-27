package com.example.ddangddangddang.domain.directmessage.dto;

/**
 * 읽음 처리. lastReadMessageId를 보내지 않으면(null) 방의 마지막 메시지까지 읽은 것으로 한다.
 * 화면에 보인 메시지까지만 읽음 처리하고 싶을 때 마지막으로 보인 메시지 ID를 보낸다.
 */
public record DmReadRequest(Long lastReadMessageId) {
}
