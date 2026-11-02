package com.example.ddangddangddang.domain.donation.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 판매자가 도네이션 마일리지로 산 혜택 한 번. startsAt ~ endsAt 동안 효과가 있다.
 * 파워 판매자는 영구라 endsAt이 PERMANENT_END다. (예전에 30일짜리로 산 것도 영구로 옮겼다: docs/migrations/2026-10-28-power-seller-permanent.sql)
 */
@Getter
@Entity
@Table(
        name = "seller_perks",
        indexes = {
                // 내 혜택 / 진행 중인 혜택의 끝나는 시각
                @Index(name = "idx_seller_perk_seller_type_ends", columnList = "seller_id, type, ends_at"),
                // 지금 파워 판매자인 판매자들 (라이브 목록)
                @Index(name = "idx_seller_perk_type_ends", columnList = "type, ends_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerPerk extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SellerPerkType type;

    // 산 가격 (받은 도네이션 포인트)
    @Column(nullable = false)
    private long price;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    private SellerPerk(Long sellerId, SellerPerkType type, long price, LocalDateTime startsAt, LocalDateTime endsAt) {
        this.sellerId = sellerId;
        this.type = type;
        this.price = price;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    /** 끝나지 않는 혜택의 endsAt */
    public static final LocalDateTime PERMANENT_END = LocalDateTime.of(9999, 12, 31, 0, 0);

    /** 끝나지 않는 혜택 (파워 판매자) */
    public static SellerPerk permanent(Long sellerId, SellerPerkType type, long price, LocalDateTime now) {
        return new SellerPerk(sellerId, type, price, now, PERMANENT_END);
    }
}
