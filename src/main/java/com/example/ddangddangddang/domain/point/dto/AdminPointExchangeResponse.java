package com.example.ddangddangddang.domain.point.dto;

import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointExchangeType;

import java.time.LocalDateTime;

/**
 * 관리자용 환전 신청 정보. 관리자가 직접 송금해야 하므로 계좌번호를 가리지 않고 전부 보여준다.
 * 사용자용 PointExchangeResponse와 섞어 쓰지 않는다.
 */
public record AdminPointExchangeResponse(
        Long exchangeId,
        Long userId,
        long amount,
        PointExchangeType status,
        String bankCode,
        String accountNumber,
        String accountHolder,
        String failReason,
        Long handledBy,
        LocalDateTime processingStartedAt,
        LocalDateTime createdAt
) {

    public static AdminPointExchangeResponse from(PointExchange exchange) {
        return new AdminPointExchangeResponse(
                exchange.getId(),
                exchange.getUserId(),
                exchange.getAmount(),
                exchange.getStatus(),
                exchange.getBankCode(),
                exchange.getAccountNumber(),
                exchange.getAccountHolder(),
                exchange.getFailReason(),
                exchange.getHandledBy(),
                exchange.getProcessingStartedAt(),
                exchange.getCreatedAt()
        );
    }
}
