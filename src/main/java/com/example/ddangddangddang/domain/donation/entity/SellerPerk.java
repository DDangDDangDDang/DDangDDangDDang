package com.example.ddangddangddang.domain.donation.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 판매자가 받은 도네이션 포인트로 산 혜택 한 번. startsAt ~ endsAt 동안 효과가 있다.
 * 이미 같은 혜택이 진행 중일 때 또 사면, 진행 중인 혜택이 끝나는 시각부터 이어 붙인다. (기간이 겹치지 않음)
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

    public static SellerPerk of(Long sellerId, SellerPerkType type, long price, LocalDateTime startsAt, LocalDateTime endsAt) {
        return new SellerPerk(sellerId, type, price, startsAt, endsAt);
    }
}
