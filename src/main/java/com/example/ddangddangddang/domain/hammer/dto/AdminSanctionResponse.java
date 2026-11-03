package com.example.ddangddangddang.domain.hammer.dto;

import com.example.ddangddangddang.domain.hammer.entity.SanctionType;

import java.time.LocalDateTime;

/**
 * 관리자 검토 대상: 최근 90일 본인 책임 취소가 4회 이상인 회원. (정지가 끝났어도 기록이 90일 안이면 나온다)
 *
 * @param bannedUntil 정지가 풀리는 시각 (이미 지났을 수 있다)
 */
public record AdminSanctionResponse(Long userId, String nickname, SanctionType type, int cancelCount,
                                    LocalDateTime bannedUntil) {
}
