package com.example.ddangddangddang.domain.livestream.dto;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;

import java.time.LocalDateTime;

public record LiveStreamResponse(
        Long id,
        Long sellerId,
        String title,
        LiveStreamStatus status,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
    public static LiveStreamResponse from(LiveStream liveStream) {
        return new LiveStreamResponse(
                liveStream.getId(),
                liveStream.getSellerId(),
                liveStream.getTitle(),
                liveStream.getStatus(),
                liveStream.getCreatedAt(),
                liveStream.getStartedAt(),
                liveStream.getEndedAt()
        );
    }
}
