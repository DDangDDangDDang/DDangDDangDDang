package com.example.ddangddangddang.domain.hammer.dto;

import com.example.ddangddangddang.domain.hammer.entity.SanctionType;

import java.time.LocalDateTime;

/**
 * 지금 걸려 있는 정지.
 *
 * @param bannedUntil 이 시각에 풀린다
 * @param cancelCount 정지를 부른 최근 90일 본인 책임 취소 수
 */
public record SanctionResponse(SanctionType type, LocalDateTime bannedUntil, int cancelCount) {
}
