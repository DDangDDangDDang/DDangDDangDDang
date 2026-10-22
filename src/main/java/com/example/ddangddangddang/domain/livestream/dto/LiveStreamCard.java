package com.example.ddangddangddang.domain.livestream.dto;

import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 방송 카드 (홈 화면, 방송 목록·검색).
 * viewerCount: 현재 시청자 수 (판매자 제외), viewerIncrease: 최근 5분(설정값) 동안 늘어난 시청자 수. 방송 중이 아니거나 집계 전이면 0
 * scheduledStartAt: 방송 시작 예정 시간 (정하지 않았으면 null), startedAt·endedAt: 실제 시작·종료 시간
 * sellerNickname: 탈퇴 등으로 회원을 찾지 못하면 null
 */
public record LiveStreamCard(
        Long liveStreamId,
        String title,
        // 해시태그 (# 없이, 없으면 빈 목록)
        List<String> hashtags,
        LiveStreamStatus status,
        Long sellerId,
        String sellerNickname,
        LocalDateTime scheduledStartAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int viewerCount,
        int viewerIncrease
) {
}
