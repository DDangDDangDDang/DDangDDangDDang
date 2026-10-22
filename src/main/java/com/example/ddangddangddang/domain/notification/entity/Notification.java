package com.example.ddangddangddang.domain.notification.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림함의 알림 한 건. 접속 중이면 /user/queue/notifications로도 바로 보낸다.
 * 판매자 닉네임·방송 제목은 알림을 만든 때의 값으로 남긴다. (나중에 바뀌어도 알림 문구는 그대로)
 */
@Entity
@Table(
        name = "notifications",
        // 내 알림 목록(최신순)과 안 읽은 알림 수
        indexes = @Index(name = "idx_notification_user", columnList = "user_id, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림을 받는 회원
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "seller_nickname", length = 20)
    private String sellerNickname;

    @Column(name = "live_stream_id")
    private Long liveStreamId;

    @Column(name = "live_title", length = 100)
    private String liveTitle;

    // 읽은 시각. 안 읽었으면 null
    @Column(name = "read_at")
    private LocalDateTime readAt;

    private Notification(Long userId, NotificationType type, Long sellerId, String sellerNickname, Long liveStreamId, String liveTitle) {
        this.userId = userId;
        this.type = type;
        this.sellerId = sellerId;
        this.sellerNickname = sellerNickname;
        this.liveStreamId = liveStreamId;
        this.liveTitle = liveTitle;
    }

    public static Notification liveStarted(Long userId, Long sellerId, String sellerNickname, Long liveStreamId, String liveTitle) {
        return new Notification(userId, NotificationType.LIVE_STARTED, sellerId, sellerNickname, liveStreamId, liveTitle);
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void read(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
