package com.example.ddangddangddang.domain.livestream.dto;

/**
 * /topic/live-streams/{liveStreamId} 구독자에게 보내는 방송 정보 변경 알림.
 * 바뀐 값만이 아니라 방송 전체 정보를 담아, 클라이언트는 받은 값으로 화면을 덮어쓰면 된다.
 */
public record LiveStreamUpdatedMessage(String type, LiveStreamResponse liveStream) {

    public static LiveStreamUpdatedMessage of(LiveStreamResponse liveStream) {
        return new LiveStreamUpdatedMessage("UPDATED", liveStream);
    }
}
