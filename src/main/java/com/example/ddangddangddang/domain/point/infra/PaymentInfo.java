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

    /** 결제가 완료됐다가 일부만 취소된 상태 (포트원 콘솔에서 부분 취소한 경우 등) */
    public boolean isPartiallyCancelled() {
        return status == PaymentStatus.PARTIAL_CANCELLED;
    }

    public boolean isInProgress() {
        return !isPaid() && !status.isTerminalFailure();
    }
}
