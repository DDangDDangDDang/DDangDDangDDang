package com.example.ddangddangddang.domain.donation.entity;

import com.example.ddangddangddang.domain.donation.exception.DonationErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도네이션 포인트 지갑. 현금 포인트(PointWallet)와 따로 두고, 환전할 수 없다.
 * - giftableBalance: 구매 확정으로 적립해 판매자에게 선물할 수 있는 포인트. 유효기간이 있어 적립 묶음(DonationPointLot)의 남은 양 합계와 같다.
 * - receivedBalance: 판매자가 선물받은 포인트. 판매자 상점(파워 판매자 등)에만 쓴다.
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

    @Column(name = "giftable_balance", nullable = false)
    private long giftableBalance;

    @Column(name = "received_balance", nullable = false)
    private long receivedBalance;

    @Version
    private Long version;

    private DonationWallet(Long userId) {
        this.userId = userId;
    }

    public static DonationWallet create(Long userId) {
        return new DonationWallet(userId);
    }

    /** 구매 확정 적립 */
    public void earn(long amount) {
        validatePositive(amount);
        this.giftableBalance += amount;
    }

    /** 선물하기 / 유효기간 만료: 선물할 수 있는 포인트 차감 */
    public void useGiftable(long amount) {
        validatePositive(amount);
        if (giftableBalance < amount) {
            throw new BusinessException(DonationErrorCode.INSUFFICIENT_BALANCE);
        }
        this.giftableBalance -= amount;
    }

    /** 선물받음 */
    public void receive(long amount) {
        validatePositive(amount);
        this.receivedBalance += amount;
    }

    /** 판매자 상점 구매: 받은 포인트 차감 */
    public void spendReceived(long amount) {
        validatePositive(amount);
        if (receivedBalance < amount) {
            throw new BusinessException(DonationErrorCode.INSUFFICIENT_RECEIVED);
        }
        this.receivedBalance -= amount;
    }

    private static void validatePositive(long amount) {
        if (amount <= 0) {
            throw new BusinessException(DonationErrorCode.INVALID_GIFT_AMOUNT);
        }
    }
}
