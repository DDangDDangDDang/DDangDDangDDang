package com.example.ddangddangddang.domain.seller.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자 구독. 구독한 판매자가 방송을 시작하면 알림을 받는다. (LiveStartedNotifier)
 * 같은 판매자를 두 번 구독하지 못하도록 (subscriber_id, seller_id)를 유니크로 둔다.
 */
@Entity
@Table(
        name = "seller_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_seller_subscription", columnNames = {"subscriber_id", "seller_id"}),
        // 판매자의 구독자 수·방송 시작 알림 대상 조회
        indexes = @Index(name = "idx_seller_subscription_seller", columnList = "seller_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    private SellerSubscription(Long subscriberId, Long sellerId) {
        this.subscriberId = subscriberId;
        this.sellerId = sellerId;
    }

    public static SellerSubscription create(Long subscriberId, Long sellerId) {
        return new SellerSubscription(subscriberId, sellerId);
    }
}
