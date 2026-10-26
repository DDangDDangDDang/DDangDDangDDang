package com.example.ddangddangddang.domain.point.dto;

import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointExchangeType;
import com.example.ddangddangddang.domain.user.entity.User;

import java.time.LocalDateTime;

/**
 * 관리자용 환전 신청 정보. 관리자가 직접 송금해야 하므로 계좌번호를 가리지 않고 전부 보여준다.
 * 사용자용 PointExchangeResponse와 섞어 쓰지 않는다.
 *
 * @param nickname 신청한 회원 닉네임. 탈퇴 등으로 회원을 찾지 못하면 null
 * @param email    신청한 회원 이메일. 회원을 찾지 못하면 null
 */
public record AdminPointExchangeResponse(
        Long exchangeId,
        Long userId,
        String nickname,
        String email,
        long amount,
        PointExchangeType status,
        String bankCode,
        String accountNumber,
        String accountHolder,
        String failReason,
        Long handledBy,
        LocalDateTime processingStartedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminPointExchangeResponse from(PointExchange exchange, User user) {
        return new AdminPointExchangeResponse(
                exchange.getId(),
                exchange.getUserId(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getEmail(),
                exchange.getAmount(),
                exchange.getStatus(),
                exchange.getBankCode(),
                exchange.getAccountNumber(),
                exchange.getAccountHolder(),
                exchange.getFailReason(),
                exchange.getHandledBy(),
                exchange.getProcessingStartedAt(),
                exchange.getCreatedAt(),
                exchange.getUpdatedAt()
        );
    }
}
