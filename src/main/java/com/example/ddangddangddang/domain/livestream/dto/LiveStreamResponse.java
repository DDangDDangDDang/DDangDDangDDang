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
        // 영상 위에 띄운 경매 ID, 띄운 순서로 최대 2개 (없으면 빈 목록). 경매 정보는 이 방송의 경매 목록(GET /lives/{id}/auctions)에서 찾는다.
        List<Long> featuredAuctionIds,
        // 판매자가 정한 화면 배치 (경매 카드·선물·입찰·낙찰 안내 위치). 정하지 않았으면 null이고 화면은 기본 배치로 그린다.
        OverlayLayoutResponse overlayLayout,
        // 카메라 대신 미리 녹화한 영상을 내보내는 방송인지 (시청 화면에 "녹화 영상" 표시)
        boolean recorded,
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
                liveStream.getFeaturedAuctionIds(),
                OverlayLayoutResponse.from(liveStream.getOverlayLayout()),
                liveStream.isRecorded(),
                liveStream.getStatus(),
                liveStream.getCreatedAt(),
                liveStream.getScheduledStartAt(),
                liveStream.getStartedAt(),
                liveStream.getEndedAt()
        );
    }
}
