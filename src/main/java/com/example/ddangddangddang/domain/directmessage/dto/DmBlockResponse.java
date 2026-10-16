package com.example.ddangddangddang.domain.directmessage.dto;

import java.time.LocalDateTime;

/**
 * 내가 차단한 회원. 탈퇴 등으로 회원을 찾지 못하면 nickname은 null
 */
public record DmBlockResponse(Long userId, String nickname, LocalDateTime blockedAt) {
}
