package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
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
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;

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

    /** 비관적 락을 쓰지 않는 경로에서 동시 수정이 일어났을 때를 대비한 안전장치 */
    @Version
    private Long version;

    private PointWallet(Long userId) {
        this.userId = userId;
        this.balance = 0L;
        this.heldBalance = 0L;
    }

    public static PointWallet create(Long userId) {
        return new PointWallet(userId);
    }

    /** 충전: 사용 가능 + */
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
        this.heldBalance -= amount;
    }

    /** 즉시구매: 사용 가능 포인트 바로 차감 */
    public void purchase(long amount) {
        validateAmount(amount);
        if (balance < amount) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
    }

    /** 구매 확정 정산: 판매자 사용 가능 + */
    public void settle(long amount) {
        validateAmount(amount);
        this.balance += amount;
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