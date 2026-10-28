package com.example.ddangddangddang.domain.shop.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 경매 위로 올리기: startsAt ~ endsAt(3시간) 동안 경매 목록(최신순) 맨 위에 "홍보"로 보인다. 끝나면 원래 순서로 돌아간다.
 * - auctionId 유니크: 경매 하나는 한 번만 올릴 수 있다. (끝난 뒤에도 다시 올릴 수 없음)
 * - 동시에 올라가 있는 경매는 MAX_ACTIVE개까지. 첫 페이지가 홍보로만 차지 않게 한다.
 */
@Getter
@Entity
@Table(name = "auction_boosts",
        uniqueConstraints = @UniqueConstraint(name = "uk_auction_boost_auction", columnNames = "auction_id"),
        indexes = @Index(name = "idx_auction_boost_ends", columnList = "ends_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionBoost extends BaseTimeEntity {

    public static final Duration DURATION = Duration.ofHours(3);
    public static final int MAX_ACTIVE = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    private AuctionBoost(Long auctionId, Long sellerId, LocalDateTime startsAt) {
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.startsAt = startsAt;
        this.endsAt = startsAt.plus(DURATION);
    }

    public static AuctionBoost start(Long auctionId, Long sellerId, LocalDateTime now) {
        return new AuctionBoost(auctionId, sellerId, now);
    }
}
