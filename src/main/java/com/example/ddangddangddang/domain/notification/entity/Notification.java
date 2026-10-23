package com.example.ddangddangddang.domain.notification.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림함의 알림 한 건. 접속 중이면 /user/queue/notifications로, 브라우저 알림을 켠 기기에는 웹 푸시로도 보낸다.
 * 판매자 닉네임·방송 제목·상품명·금액은 알림을 만든 때의 값으로 남긴다. (나중에 바뀌어도 알림 문구는 그대로)
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

    // 방송 시작 알림: 미리 녹화한 영상 방송인지. 다른 알림은 null
    @Column(name = "live_recorded")
    private Boolean liveRecorded;

    // 경매 알림: 경매, 그때의 상품명, 금액(새 최고가·낙찰가)
    @Column(name = "auction_id")
    private Long auctionId;

    @Column(name = "product_name", length = 100)
    private String productName;

    @Column(name = "price")
    private Long price;

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
        return liveStarted(userId, sellerId, sellerNickname, liveStreamId, liveTitle, false);
    }

    /** recorded: 미리 녹화한 영상 방송이면 true (알림 문구에 "녹화 영상"을 붙인다) */
    public static Notification liveStarted(Long userId, Long sellerId, String sellerNickname, Long liveStreamId, String liveTitle,
                                           boolean recorded) {
        Notification notification = new Notification(userId, NotificationType.LIVE_STARTED, sellerId, sellerNickname, liveStreamId, liveTitle);
        notification.liveRecorded = recorded;
        return notification;
    }

    /** 미리 녹화한 영상 방송의 시작 알림인지 */
    public boolean isLiveRecorded() {
        return Boolean.TRUE.equals(liveRecorded);
    }

    /** 경매 알림. type은 AUCTION_으로 시작하는 것, price는 없으면 null */
    public static Notification auction(Long userId, NotificationType type, Long auctionId, String productName, Long price) {
        Notification notification = new Notification(userId, type, null, null, null, null);
        notification.auctionId = auctionId;
        notification.productName = productName;
        notification.price = price;
        return notification;
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
