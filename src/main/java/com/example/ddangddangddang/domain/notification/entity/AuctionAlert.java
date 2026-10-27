package com.example.ddangddangddang.domain.notification.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경매 알림 받기. 받기로 한 경매가 시작·마감 임박·취소·종료되면 알린다. (AuctionNotifier)
 * 입찰한 회원은 이것이 없어도 마감 임박·취소·종료 알림을 받는다.
 * 같은 경매를 두 번 받지 못하도록 (user_id, auction_id)를 유니크로 둔다.
 */
@Entity
@Table(
        name = "auction_alerts",
        uniqueConstraints = @UniqueConstraint(name = "uk_auction_alert", columnNames = {"user_id", "auction_id"}),
        // 경매의 알림 대상 조회
        indexes = @Index(name = "idx_auction_alert_auction", columnList = "auction_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionAlert extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    private AuctionAlert(Long userId, Long auctionId) {
        this.userId = userId;
        this.auctionId = auctionId;
    }

    public static AuctionAlert create(Long userId, Long auctionId) {
        return new AuctionAlert(userId, auctionId);
    }
}
