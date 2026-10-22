package com.example.ddangddangddang.domain.livestream.event;

/**
 * 방송이 시작되어 커밋된 뒤 처리할 일(채팅 시작 안내)을 다른 도메인에 알린다.
 * 시청 화면의 방송 상태는 함께 발행하는 LiveStreamUpdatedEvent로 바뀐다.
 */
public record LiveStreamStartedEvent(Long liveStreamId) {
}
