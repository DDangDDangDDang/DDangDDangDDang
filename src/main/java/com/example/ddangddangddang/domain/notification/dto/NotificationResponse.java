package com.example.ddangddangddang.domain.notification.dto;

import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationContent;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;

/**
 * 알림. REST 목록과 실시간 메시지(/user/queue/notifications)가 같은 형식이다.
 * LIVE_STARTED: sellerId·sellerNickname이 liveStreamId 방송(liveTitle)을 시작함. liveRecorded면 미리 녹화한 영상 방송
 * AUCTION_*: auctionId 경매(productName). price는 OUTBID면 새 현재가, WON·SOLD면 낙찰가
 * title·body·link: 화면에 그대로 보여 줄 문구와 누르면 갈 경로 (브라우저 알림과 같은 문구)
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        Long sellerId,
        String sellerNickname,
        Long liveStreamId,
        String liveTitle,
        boolean liveRecorded,
        Long auctionId,
        String productName,
        Long price,
        String title,
        String body,
        String link,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        NotificationContent content = NotificationContent.of(notification);
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getSellerId(),
                notification.getSellerNickname(), notification.getLiveStreamId(), notification.getLiveTitle(), notification.isLiveRecorded(),
                notification.getAuctionId(), notification.getProductName(), notification.getPrice(),
                content.title(), content.body(), content.link(), notification.isRead(), notification.getCreatedAt());
    }
}
