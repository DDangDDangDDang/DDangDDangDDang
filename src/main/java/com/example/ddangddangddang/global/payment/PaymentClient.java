package com.example.ddangddangddang.global.payment;

/**
 * PG사 결제 승인 클라이언트. 테스트에서는 Mock으로 바꿔 끼운다.
 */
public interface PaymentClient {

    /**
     * 결제를 승인한다.
     *
     * @throws PaymentConfirmException 승인에 실패했거나 결과를 알 수 없을 때
     */
    PaymentConfirmResult confirm(String paymentKey, String orderId, long amount);
}