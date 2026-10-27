package com.example.ddangddangddang.domain.point.infra;

/**
 * 포인트 충전용 결제 조회·취소 (포트원).
 *
 * global.payment.PaymentClient(토스페이먼츠 결제 승인용)와 이름이 겹쳐 헷갈리지 않도록
 * PaymentGateway로 이름을 바꿨다.
 */
public interface PaymentGateway {

    /** 결제 단건 조회 */
    PaymentInfo getPayment(String paymentId);

    /** 결제 전액 취소 */
    void cancelPayment(String paymentId, String reason);
}
