package com.example.ddangddangddang.domain.point.dto;

import com.example.ddangddangddang.domain.point.entity.PointCharge;

public record PointChargeResponse(
        Long chargeId,
        String paymentId,
        long amount,
        String orderName
) {

    public static PointChargeResponse from(PointCharge charge) {
        return new PointChargeResponse(
                charge.getId(),
                charge.getPaymentId(),
                charge.getAmount(),
                String.format("%,d 포인트 충전", charge.getAmount())
        );
    }
}
