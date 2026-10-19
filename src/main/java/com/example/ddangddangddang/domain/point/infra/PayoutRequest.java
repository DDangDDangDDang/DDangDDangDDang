package com.example.ddangddangddang.domain.point.infra;

public record PayoutRequest(
        String payoutId,
        long amount,
        String bankCode,
        String accountNumber,
        String accountHolder
) {
}
