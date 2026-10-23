package com.example.ddangddangddang.domain.donation.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도네이션 포인트 원장. 수정·삭제하지 않고 쌓기만 한다. (현금 포인트의 PointHistory와 같은 방식)
 * 변동 뒤 두 잔액을 함께 남겨 잔액이 틀어진 시점을 찾을 수 있게 한다.
 * refId는 type에 따라 주문(EARN), 선물(GIFT·RECEIVE), 적립 묶음(EXPIRE), 판매자 혜택(SPEND)의 ID다.
 */
@Getter
@Entity
@Table(name = "donation_point_histories", indexes = @Index(name = "idx_donation_history_user", columnList = "user_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DonationPointHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationPointHistoryType type;

    /** 변동 금액. 방향은 type으로 판단하고 값은 항상 양수다. */
    @Column(nullable = false)
    private long amount;

    @Column(name = "giftable_after", nullable = false)
    private long giftableAfter;

    @Column(name = "received_after", nullable = false)
    private long receivedAfter;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    private DonationPointHistory(DonationWallet wallet, DonationPointHistoryType type, long amount, Long refId) {
        this.userId = wallet.getUserId();
        this.type = type;
        this.amount = amount;
        this.giftableAfter = wallet.getGiftableBalance();
        this.receivedAfter = wallet.getReceivedBalance();
        this.refId = refId;
    }

    /** 지갑을 바꾼 뒤에 부른다. (바뀐 잔액을 남긴다) */
    public static DonationPointHistory of(DonationWallet wallet, DonationPointHistoryType type, long amount, Long refId) {
        return new DonationPointHistory(wallet, type, amount, refId);
    }
}
