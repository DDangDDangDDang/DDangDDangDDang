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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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
}