package com.example.ddangddangddang.domain.directmessage.dto;

/**
 * 내 모든 대화방의 읽지 않은 메시지 수 합계. (상단 알림 배지용)
 */
public record DmUnreadCountResponse(long unreadCount) {
}
