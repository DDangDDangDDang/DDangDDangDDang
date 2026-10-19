package com.example.ddangddangddang.domain.livestream.event;

/**
 * 방송이 종료되어 커밋된 뒤 처리할 일(채팅 종료 안내, 진행 중 경매 처리 등)을 다른 도메인에 알린다.
 */
public record LiveStreamEndedEvent(Long liveStreamId) {
}
