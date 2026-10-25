package com.example.ddangddangddang.domain.donation.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 선물: 시청자(giver)가 방송 중인 판매자(seller)에게 보낸 도네이션 포인트. 수정·삭제하지 않는다.
 * 한 달 선물 한도와 판매자 신뢰 지표(응원한 사람 수·받은 포인트)를 이 표에서 센다.
 * (giver_id, request_key) 유니크: 같은 선물 요청을 두 번 보내도(연타·재전송) 한 번만 선물한다.
 */
@Getter
@Entity
@Table(
        name = "donations",
        uniqueConstraints = @UniqueConstraint(name = "uk_donation_request", columnNames = {"giver_id", "request_key"}),
        indexes = {
                // 한 달 한도: 이 사람이 이 판매자에게 이번 달 보낸 합계
                @Index(name = "idx_donation_giver_seller_created", columnList = "giver_id, seller_id, created_at"),
                // 판매자 신뢰 지표: 최근 기간 받은 선물
                @Index(name = "idx_donation_seller_created", columnList = "seller_id, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Donation extends BaseTimeEntity {

    public static final int MAX_MESSAGE_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "giver_id", nullable = false)
    private Long giverId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "live_stream_id", nullable = false)
    private Long liveStreamId;

    @Column(nullable = false)
    private long amount;

    // 선물과 함께 보낸 한마디. 없으면 null
    @Column(length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "request_key", nullable = false, length = 64)
    private String requestKey;

    private Donation(Long giverId, Long sellerId, Long liveStreamId, long amount, String message, String requestKey) {
        this.giverId = giverId;
        this.sellerId = sellerId;
        this.liveStreamId = liveStreamId;
        this.amount = amount;
        this.message = message;
        this.requestKey = requestKey;
    }

    public static Donation of(Long giverId, Long sellerId, Long liveStreamId, long amount, String message, String requestKey) {
        return new Donation(giverId, sellerId, liveStreamId, amount, message, requestKey);
    }
}
