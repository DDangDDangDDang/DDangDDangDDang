package com.example.ddangddangddang.domain.point.dto;

import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.point.entity.PointChargeType;

public record PointChargeCompleteResponse(
        Long chargeId,
        String paymentId,
        long amount,
        PointChargeType status,
        Long balance
) {

    public static PointChargeCompleteResponse of(PointCharge charge, Long balance) {
        return new PointChargeCompleteResponse(
                charge.getId(),
                charge.getPaymentId(),
                charge.getAmount(),
                charge.getStatus(),
                balance
        );
    }
}