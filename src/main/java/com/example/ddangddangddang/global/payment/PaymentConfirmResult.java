package com.example.ddangddangddang.global.payment;

import java.time.LocalDateTime;

public record PaymentConfirmResult(
        String paymentKey,
        String orderId,
        long totalAmount,
        String method,
        LocalDateTime approvedAt
) {
}