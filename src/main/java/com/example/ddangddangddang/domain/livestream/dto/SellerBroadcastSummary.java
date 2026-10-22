package com.example.ddangddangddang.domain.livestream.dto;

import java.time.LocalDateTime;

/**
 * 판매자가 시작한 방송 집계 (판매자 프로필). 방송 엔티티를 모두 읽지 않고 쿼리 한 번으로 구한다.
 * firstStartedAt: 첫 방송 시작 (방송한 적 없으면 null), broadcastCount: 시작한 방송 수
 */
public record SellerBroadcastSummary(LocalDateTime firstStartedAt, Long broadcastCount) {
}
