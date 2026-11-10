package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포인트 원장. 수정·삭제하지 않고 쌓기만 한다.
 * 변동 후 잔액을 함께 기록해 두면 잔액이 맞지 않을 때 어느 시점에서 틀어졌는지 추적할 수 있다.
 */
@Getter
@Entity
@Table(name = "point_histories", indexes = @Index(name = "idx_point_histories_wallet_id", columnList = "walletId, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    // MySQL ENUM이 아니라 문자열로 둔다. (값을 더할 때 ddl-auto가 ENUM 정의를 고치지 않을 수 있음)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PointHistoryType type;

    /** 변동 금액. 방향은 type으로 판단하고 값은 항상 양수다. */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long balanceAfter;

    @Column(nullable = false)
    private long heldBalanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointReferenceType refType;

    @Column(nullable = false)
    private Long refId;

    /**
     * 구매(PURCHASE) 금액 중 번 포인트로 낸 몫. 주문이 취소되면 이만큼을 번 포인트(환전 가능)로 돌려준다.
     * 다른 종류의 내역은 0이다.
     */
    @ColumnDefault("0")
    @Column(nullable = false)
    private long earnedPortion;

    private PointHistory(Long walletId, PointHistoryType type, long amount, long balanceAfter,
                         long heldBalanceAfter, PointReferenceType refType, Long refId) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.heldBalanceAfter = heldBalanceAfter;
        this.refType = refType;
        this.refId = refId;
    }

    public static PointHistory of(PointWallet wallet, PointHistoryType type, long amount,
                                  PointReferenceType refType, Long refId) {
        return new PointHistory(wallet.getId(), type, amount, wallet.getBalance(),
                wallet.getHeldBalance(), refType, refId);
    }

    /** 구매 내역: 번 포인트로 낸 몫을 함께 남긴다. */
    public static PointHistory purchase(PointWallet wallet, long amount, long earnedPortion, Long auctionId) {
        PointHistory history = of(wallet, PointHistoryType.PURCHASE, amount, PointReferenceType.AUCTION, auctionId);
        history.earnedPortion = earnedPortion;
        return history;
    }
}