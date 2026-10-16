package com.example.ddangddangddang.domain.livestream.dto;

import java.time.LocalDateTime;

/**
 * 홈 화면 방송 카드.
 * viewerCount: 현재 시청자 수 (판매자 제외), viewerIncrease: 최근 5분(설정값) 동안 늘어난 시청자 수. 집계 전이면 0
 * sellerNickname: 탈퇴 등으로 회원을 찾지 못하면 null
 */
public record LiveStreamCard(
        Long liveStreamId,
        String title,
        Long sellerId,
        String sellerNickname,
        LocalDateTime startedAt,
        int viewerCount,
        int viewerIncrease
) {
}
