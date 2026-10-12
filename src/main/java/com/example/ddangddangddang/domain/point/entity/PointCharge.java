package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Table(name = "point_charges")
@NoArgsConstructor
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

    public static PointCharge create(Long userId, Long amount, String paymentId){
        if (userId == null){
            throw new IllegalArgumentException("유저 아이디가 일치하지 않습니다.");
        }
        if (amount <= 0){
            throw new BusinessException(PointErrorCode.INVALID_POINT_AMOUNT);
        }
        if (paymentId == null || paymentId.isEmpty()){
            throw new BusinessException(PointErrorCode.INVALID_PAYMENT_ID);
        }

        return new PointCharge(userId, amount, paymentId);
    }

    private void validatePending() {
        if (this.status != PointChargeType.PENDING) {
            throw new BusinessException(PointErrorCode.INVALID_CHARGE_STATUS);
        }
    }

    //거래 성공시 상태변경
    public void complete(){
        validatePending();
        this.status = PointChargeType.COMPLETE;
    }

    //거래 실패시 상태변경
    public void fail() {
        validatePending();
        this.status = PointChargeType.FAILED;
    }

}
