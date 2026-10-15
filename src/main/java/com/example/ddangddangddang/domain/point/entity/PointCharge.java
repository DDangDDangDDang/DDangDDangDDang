package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "point_charges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointCharge extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, unique = true)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointChargeType status;

    private PointCharge(Long userId, Long amount, String paymentId){
        this.userId = userId;
        this.amount = amount;
        this.paymentId = paymentId;
        this.status = PointChargeType.PENDING;
    }

    public static PointCharge create(Long userId, Long amount, String paymentId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (amount == null || amount <= 0) {
            throw new BusinessException(PointErrorCode.INVALID_POINT_AMOUNT);
        }
        if (paymentId == null || paymentId.isBlank()) {
            throw new BusinessException(PointErrorCode.INVALID_PAYMENT_ID);
        }

        return new PointCharge(userId, amount, paymentId);
    }



    public boolean isPending() {
        return this.status == PointChargeType.PENDING;
    }

    public boolean isCompleted() {
        return this.status == PointChargeType.COMPLETE;
    }




    // 본인인증(본인 소유 권한 확인)
    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new BusinessException(PointErrorCode.CHARGE_ACCESS_DENIED);
        }
    }

    // PG사와 실결제금액 대조
    public boolean matchesAmount(long paidAmount) {
        return this.amount == paidAmount;
    }

    // ===== 상태 변경 =====

    // 거래 성공 상태변경
    public void complete() {
        validatePending();
        this.status = PointChargeType.COMPLETE;
    }

    // 거래 실패 상태변경
    public void fail() {
        validatePending();
        this.status = PointChargeType.FAILED;
    }

    private void validatePending() {
        if (!isPending()) {
            throw new BusinessException(PointErrorCode.INVALID_CHARGE_STATUS);
        }
    }

}
