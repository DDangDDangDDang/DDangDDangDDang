package com.example.ddangddangddang.domain.point.infra;

public interface PaymentClient {

    /** 결제 단건 조회 */
    PaymentInfo getPayment(String paymentId);

    /** 결제 전액 취소 */
    void cancelPayment(String paymentId, String reason);
}
