package com.example.ddangddangddang.domain.point.infra;

public record PaymentInfo(
        String paymentId,
        PaymentStatus status,
        long totalAmount
) {

    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    public boolean isInProgress() {
        return !isPaid() && !status.isTerminalFailure();
    }
}
