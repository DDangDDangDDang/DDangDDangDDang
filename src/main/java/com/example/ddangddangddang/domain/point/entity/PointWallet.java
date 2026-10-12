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

    /** 빼거나 더할 포인트의 수량이 양수가 되게 검증 **/
    public void validatePositiveAmount(long amount) {
        if( amount < 0 ) {
            throw new BusinessException(PointErrorCode.INVALID_POINT_AMOUNT);
        }
    }

    /** 현금으로 포인트를 충전할때 사용 **/
    public void addBalance(long amount) {
        validatePositiveAmount(amount);
        this.balance = Math.addExact(this.balance, amount);
    }

    /** 포인트에서 현금으로 전환할때 사용 **/
    public void subBalance(long amount) {

        validatePositiveAmount(amount);

        if ( this.balance - this.heldBalance < amount) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_POINT);
        }

        this.balance -= amount;
    }
}