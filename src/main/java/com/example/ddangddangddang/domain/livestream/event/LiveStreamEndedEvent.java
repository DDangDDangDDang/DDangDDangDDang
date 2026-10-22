package com.example.ddangddangddang.domain.livestream.event;

/**
 * 방송이 종료되어 커밋된 뒤 처리할 일(채팅 종료 안내, 진행 중 경매 처리 등)을 다른 도메인에 알린다.
 *
 * @param idle 판매자 송출이 오래 없어 서버가 자동으로 종료했으면 true (LiveStreamIdleWatcher)
 */
public record LiveStreamEndedEvent(Long liveStreamId, boolean idle) {
}
