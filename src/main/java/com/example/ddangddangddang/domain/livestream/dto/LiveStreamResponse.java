package com.example.ddangddangddang.domain.livestream.dto;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * scheduledStartAt: 판매자가 정한 시작 예정 시간 (없으면 null), startedAt: 실제 시작 시간
 */
public record LiveStreamResponse(
        Long id,
        Long sellerId,
        String title,
        String description,
        // 해시태그 (# 없이, 없으면 빈 목록)
        List<String> hashtags,
        // 채팅창 고정 공지 (없으면 null)
        String notice,
        LiveStreamStatus status,
        LocalDateTime createdAt,
        LocalDateTime scheduledStartAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
    public static LiveStreamResponse from(LiveStream liveStream) {
        return new LiveStreamResponse(
                liveStream.getId(),
                liveStream.getSellerId(),
                liveStream.getTitle(),
                liveStream.getDescription(),
                liveStream.getHashtagList(),
                liveStream.getNotice(),
                liveStream.getStatus(),
                liveStream.getCreatedAt(),
                liveStream.getScheduledStartAt(),
                liveStream.getStartedAt(),
                liveStream.getEndedAt()
        );
    }
}
