package com.example.ddangddangddang.domain.point.infra;

/**
 * @param cancelledAmount 결제사에서 취소(환불)된 금액 합계. 부분 취소면 totalAmount보다 작다
 */
public record PaymentInfo(
        String paymentId,
        PaymentStatus status,
        long totalAmount,
        long cancelledAmount
) {

    /** 취소된 금액이 없는 결제 */
    public PaymentInfo(String paymentId, PaymentStatus status, long totalAmount) {
        this(paymentId, status, totalAmount, 0L);
    }

    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    public boolean isInProgress() {
        return !isPaid() && !status.isTerminalFailure();
    }
}
