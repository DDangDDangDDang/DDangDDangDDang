package com.example.ddangddangddang.domain.point.dto;

import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.user.entity.User;

/**
 * 충전 요청 생성 결과. 프론트는 이 값을 그대로 PortOne.requestPayment()에 넣는다.
 * 구매자 정보는 KG이니시스 등 일부 PG에서 필수라 함께 내려준다. (실명 필드가 없어 이름은 닉네임)
 */
public record PointChargeResponse(
        Long chargeId,
        String paymentId,
        long amount,
        String orderName,
        Customer customer
) {

    public record Customer(
            String fullName,
            String email,
            String phoneNumber
    ) {
    }

    public static PointChargeResponse of(PointCharge charge, User user) {
        return new PointChargeResponse(
                charge.getId(),
                charge.getPaymentId(),
                charge.getAmount(),
                String.format("%,d 포인트 충전", charge.getAmount()),
                new Customer(user.getNickname(), user.getEmail(), user.getPhoneNumber())
        );
    }
}
