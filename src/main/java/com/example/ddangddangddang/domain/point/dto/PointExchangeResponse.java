package com.example.ddangddangddang.domain.point.dto;

import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointExchangeType;

import java.time.LocalDateTime;

public record PointExchangeResponse(
        Long exchangeId,
        long amount,
        PointExchangeType status,
        String bankCode,
        String accountNumber,   // 마스킹된 값
        String accountHolder,
        String failReason,
        LocalDateTime createdAt
) {

    public static PointExchangeResponse from(PointExchange exchange) {
        return new PointExchangeResponse(
                exchange.getId(),
                exchange.getAmount(),
                exchange.getStatus(),
                exchange.getBankCode(),
                mask(exchange.getAccountNumber()),
                exchange.getAccountHolder(),
                exchange.getFailReason(),
                exchange.getCreatedAt()
        );
    }

    /** 앞 3자리, 뒤 3자리만 보여준다. 1101234567 → 110****567 */
    static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 6) {
            return accountNumber;
        }
        int hidden = accountNumber.length() - 6;
        return accountNumber.substring(0, 3) + "*".repeat(hidden) + accountNumber.substring(3 + hidden);
    }
}
