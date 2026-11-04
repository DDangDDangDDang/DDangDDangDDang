package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 현금 포인트 지갑. 1포인트 = 1원.
 *
 * 환전 규칙: 충전한 포인트는 환전할 수 없고, 돌려받으려면 결제를 취소(카드 환불)해야 한다.
 * 카드로 충전해 바로 계좌로 받는 신용카드 현금화와 도용 카드 세탁을 막기 위해서다.
 * 그래서 판매 정산·위약금으로 번 포인트(earnedBalance)를 따로 센다.
 * - 쓸 때(낙찰·즉시구매·위약금 지급·충전 회수)는 충전한 포인트부터 쓴다. 번 포인트는 마지막에 쓴다.
 * - 입찰 홀딩·해제는 포인트가 지갑 안에서 옮겨 갈 뿐이라 번 포인트에 영향이 없다.
 * - 주문 취소로 돌려받은 포인트는 어디서 왔는지 모르므로 충전한 포인트로 본다. (환전할 수 없는 쪽으로 안전하게)
 * 환전할 수 있는 양은 번 포인트와 사용 가능 포인트 중 작은 값이다. (번 포인트 일부가 입찰에 묶여 있을 수 있음)
 */
@Getter
@Entity
@Table(name = "point_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    /** 사용 가능 포인트 */
    @Column(nullable = false)
    private long balance;

    /** 입찰로 묶여 있는 포인트 */
    @Column(nullable = false)
    private long heldBalance;

    /**
     * 판매 정산·위약금으로 번 포인트. 사용 가능·홀딩 포인트에 섞여 있다. (0 이상, balance + heldBalance 이하)
     * 환전은 이 범위 안에서만 할 수 있다.
     */
    @Column(nullable = false)
    private long earnedBalance;

    /** 비관적 락을 쓰지 않는 경로에서 동시 수정이 일어났을 때를 대비한 안전장치 */
    @Version
    private Long version;

    private PointWallet(Long userId) {
        this.userId = userId;
        this.balance = 0L;
        this.heldBalance = 0L;
        this.earnedBalance = 0L;
    }

    public static PointWallet create(Long userId) {
        return new PointWallet(userId);
    }

    /** 지금 환전할 수 있는 포인트: 번 포인트 중 입찰에 묶이지 않은 만큼 */
    public long getExchangeableBalance() {
        return Math.max(0, Math.min(earnedBalance, balance));
    }

    /** 충전: 사용 가능 + (환전할 수 없는 포인트) */
    public void charge(long amount) {
        validateAmount(amount);
        this.balance += amount;
    }

    /** 입찰: 사용 가능 -> 홀딩 */
    public void hold(long amount) {
        validateAmount(amount);
        if (balance < amount) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
        this.heldBalance += amount;
    }

    /** 상위 입찰 발생: 홀딩 -> 사용 가능 */
    public void release(long amount) {
        validateAmount(amount);
        validateHeld(amount);
        this.heldBalance -= amount;
        this.balance += amount;
    }

    /** 낙찰: 홀딩된 포인트 차감 */
    public void purchaseWithHeld(long amount) {
        validateAmount(amount);
        validateHeld(amount);
        spendChargedFirst(amount);
        this.heldBalance -= amount;
    }

    /** 즉시구매: 사용 가능 포인트 바로 차감 */
    public void purchase(long amount) {
        validateAmount(amount);
        if (balance < amount) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_BALANCE);
        }
        spendChargedFirst(amount);
        this.balance -= amount;
    }

    /** 구매 확정 정산: 판매자 사용 가능 + (환전할 수 있는 포인트) */
    public void settle(long amount) {
        validateAmount(amount);
        this.balance += amount;
        this.earnedBalance += amount;
    }

    /** 환전 신청: 번 포인트에서만 차감한다. 충전한 포인트는 환전할 수 없다 (홀딩된 포인트도 환전할 수 없다) */
    public void exchange(long amount) {
        validateAmount(amount);
        if (balance < amount) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_BALANCE);
        }
        if (getExchangeableBalance() < amount) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_EXCHANGEABLE_BALANCE);
        }
        this.balance -= amount;
        this.earnedBalance -= amount;
    }

    /** 환전 취소·송금 실패: 차감했던 포인트를 사용 가능(번 포인트)으로 돌려준다 */
    public void refundExchange(long amount) {
        validateAmount(amount);
        this.balance += amount;
        this.earnedBalance += amount;
    }

    /** 주문 취소: 낙찰로 차감했던 포인트를 구매자의 사용 가능 포인트로 돌려준다 (환전할 수 없는 포인트로 돌아온다) */
    public void refundOrder(long amount) {
        validateAmount(amount);
        this.balance += amount;
    }

    /** 낙찰취소 위약금: 돌려받은 낙찰 금액에서 구매자가 낸다. 환불 직후라 잔액은 항상 충분하다 */
    public void payOrderPenalty(long amount) {
        validateAmount(amount);
        spendChargedFirst(amount);
        this.balance -= amount;
    }

    /** 낙찰취소 위약금: 판매자의 사용 가능 포인트(번 포인트)에 더한다 */
    public void receiveOrderPenalty(long amount) {
        validateAmount(amount);
        this.balance += amount;
        this.earnedBalance += amount;
    }

    /**
     * 충전 결제가 취소(환불)돼 그만큼 포인트를 회수한다. 사용 가능 포인트에서만 뺀다. (입찰에 묶인 포인트는 건드리지 않음)
     * 이미 써 버려 모자라면 있는 만큼만 회수한다.
     *
     * @return 실제로 회수한 포인트
     */
    public long reclaimCharge(long amount) {
        validateAmount(amount);
        long reclaimed = Math.min(amount, balance);
        if (reclaimed > 0) {
            spendChargedFirst(reclaimed);
            this.balance -= reclaimed;
        }
        return reclaimed;
    }

    /**
     * 포인트가 지갑 밖으로 나갈 때 충전한 포인트부터 쓰고, 모자란 만큼만 번 포인트에서 쓴다.
     * 반드시 balance·heldBalance를 줄이기 전에 부른다.
     */
    private void spendChargedFirst(long amount) {
        long total = balance + heldBalance;
        long charged = Math.max(0, total - earnedBalance);
        long fromEarned = Math.max(0, amount - charged);
        this.earnedBalance = Math.max(0, earnedBalance - fromEarned);
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new BusinessException(PointErrorCode.INVALID_AMOUNT);
        }
    }

    /** 홀딩 금액보다 많이 빼려 한다면 입찰 로직이 잘못된 것이므로 409로 막는다. */
    private void validateHeld(long amount) {
        if (heldBalance < amount) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_HELD_BALANCE);
        }
    }
}
