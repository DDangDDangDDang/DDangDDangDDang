package com.example.ddangddangddang.domain.livestream.dto;

import java.time.LocalDateTime;

/**
 * 송출이 멈춘 내 방송 안내 (/user/queue/live-stream, 판매자에게만).
 *
 * @param type   IDLE_WARNING: 곧 자동 종료됨 · IDLE_RESUMED: 다시 송출해 자동 종료가 취소됨 · IDLE_ENDED: 자동 종료됨
 * @param endsAt IDLE_WARNING일 때 자동 종료 예정 시각. 그 밖에는 null
 */
public record LiveStreamIdleMessage(String type, Long liveStreamId, LocalDateTime endsAt) {

    public static LiveStreamIdleMessage warning(Long liveStreamId, LocalDateTime endsAt) {
        return new LiveStreamIdleMessage("IDLE_WARNING", liveStreamId, endsAt);
    }

    public static LiveStreamIdleMessage resumed(Long liveStreamId) {
        return new LiveStreamIdleMessage("IDLE_RESUMED", liveStreamId, null);
    }

    public static LiveStreamIdleMessage ended(Long liveStreamId) {
        return new LiveStreamIdleMessage("IDLE_ENDED", liveStreamId, null);
    }
}
