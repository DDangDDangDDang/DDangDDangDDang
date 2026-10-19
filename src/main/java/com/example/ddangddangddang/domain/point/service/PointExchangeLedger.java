package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.entity.PointReferenceType;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환전에 따른 지갑 잔액 변경과 원장 기록을 한 곳에서 처리한다.
 * 신청(차감)과 취소·실패(환불)가 같은 규칙으로 기록되도록 모아뒀다.
 *
 * 반드시 호출한 쪽의 트랜잭션 안에서 실행되어야 해서 MANDATORY로 강제한다.
 * 팀 규칙(PointWalletRepository)대로 지갑은 비관적 락으로 조회한다.
 * 락 순서는 "환전 신청 → 지갑"이고, 입찰 쪽은 지갑만 잠그므로 데드락이 생기지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PointExchangeLedger {

    private final PointWalletRepository pointWalletRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /** 환전 신청 시 포인트 차감 */
    public PointWallet deduct(PointExchange exchange) {
        PointWallet wallet = findWalletForUpdate(exchange.getUserId());
        wallet.exchange(exchange.getAmount());
        record(wallet, PointHistoryType.EXCHANGE, exchange);
        return wallet;
    }

    /** 취소·실패 시 포인트 환불 */
    public PointWallet refund(PointExchange exchange) {
        PointWallet wallet = findWalletForUpdate(exchange.getUserId());
        wallet.refundExchange(exchange.getAmount());
        record(wallet, PointHistoryType.EXCHANGE_REFUND, exchange);
        return wallet;
    }

    private PointWallet findWalletForUpdate(Long userId) {
        return pointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.WALLET_NOT_FOUND));
    }

    private void record(PointWallet wallet, PointHistoryType type, PointExchange exchange) {
        pointHistoryRepository.save(PointHistory.of(
                wallet, type, exchange.getAmount(), PointReferenceType.EXCHANGE, exchange.getId()));
    }
}
