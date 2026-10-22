package com.example.ddangddangddang.domain.notification.dto;

import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;

/**
 * 알림. REST 목록과 실시간 메시지(/user/queue/notifications)가 같은 형식이다.
 * LIVE_STARTED: sellerId·sellerNickname이 liveStreamId 방송(liveTitle)을 시작함
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        Long sellerId,
        String sellerNickname,
        Long liveStreamId,
        String liveTitle,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getSellerId(),
                notification.getSellerNickname(), notification.getLiveStreamId(), notification.getLiveTitle(),
                notification.isRead(), notification.getCreatedAt());
    }
}
