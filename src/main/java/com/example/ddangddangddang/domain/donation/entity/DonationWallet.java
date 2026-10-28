package com.example.ddangddangddang.domain.donation.entity;

import com.example.ddangddangddang.domain.donation.exception.DonationErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도네이션 마일리지 지갑. 현금 포인트(PointWallet)와 따로 두고, 환전할 수 없다.
 * - balance: 쓸 수 있는 마일리지. 구매·판매 적립과 선물받은 마일리지를 구분하지 않고 선물·상점에 쓴다.
 *   모두 유효기간이 있는 적립 묶음(DonationPointLot)으로 쌓이므로 묶음의 남은 양 합계와 같다. (이 값은 그 합계의 캐시)
 * 잔액을 바꿀 때는 이 행을 잠가(findByUserIdForUpdate) 한도 계산과 차감이 겹치지 않게 한다.
 * 기존 회원도 쓸 수 있도록 처음 적립·선물·조회할 때 만든다.
 */
@Getter
@Entity
@Table(name = "donation_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DonationWallet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private long balance;

    @Version
    private Long version;

    private DonationWallet(Long userId) {
        this.userId = userId;
    }

    public static DonationWallet create(Long userId) {
        return new DonationWallet(userId);
    }

    /** 적립 (구매·판매 확정) / 선물받음 */
    public void add(long amount) {
        validatePositive(amount);
        this.balance += amount;
    }

    /** 선물하기 / 상점 구매 / 유효기간 만료 */
    public void deduct(long amount) {
        validatePositive(amount);
        if (balance < amount) {
            throw new BusinessException(DonationErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
    }

    private static void validatePositive(long amount) {
        if (amount <= 0) {
            throw new BusinessException(DonationErrorCode.INVALID_GIFT_AMOUNT);
        }
    }
}
