package com.example.ddangddangddang.domain.livestream.dto;

import java.util.List;

/**
 * 홈 화면 방송 목록.
 * - mostViewed: 시청자 많은 방송 (시청자 수 집계 전이면 최근 시작한 방송으로 채움)
 * - surging: 시청자 급증 방송 (최근 시청자가 늘어난 방송만, 증가 수 큰 순)
 * - upcoming: 방송 예정 (시작 예정 시간이 정해진 대기 중 방송, 시작 예정 시간이 가까운 순)
 * TODO: 입찰 많은 방송(mostBids)은 경매 도메인이 생기면 입찰 이벤트를 받아 추가
 */
public record LiveStreamHomeResponse(
        List<LiveStreamCard> mostViewed,
        List<LiveStreamCard> surging,
        List<LiveStreamCard> upcoming
) {
}
