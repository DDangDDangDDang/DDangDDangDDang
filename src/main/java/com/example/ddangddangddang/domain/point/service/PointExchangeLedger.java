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

 * 반드시 호출한 쪽의 트랜잭션 안에서 실행되어야 해서 MANDATORY로 강제한다.
 * 지갑 동시 수정은 PointWallet의 @Version(낙관적 락)이 막는다.

 * ※ PointHistoryType.EXCHANGE, EXCHANGE_REFUND와 PointReferenceType.EXCHANGE는
 *    지갑 담당자가 추가해야 한다 (아직 없음).
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PointExchangeLedger {

    private final PointWalletRepository pointWalletRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /** 환전 신청 시 포인트 차감 */
    public PointWallet deduct(PointExchange exchange) {
        PointWallet wallet = findWallet(exchange.getUserId());
        wallet.subBalance(exchange.getAmount());
        pointHistoryRepository.save(PointHistory.of(
                wallet, PointHistoryType.EXCHANGE, exchange.getAmount(),
                PointReferenceType.EXCHANGE, exchange.getId()));
        return wallet;
    }

    /** 취소·실패 시 포인트 환불 */
    public PointWallet refund(PointExchange exchange) {
        PointWallet wallet = findWallet(exchange.getUserId());
        wallet.addBalance(exchange.getAmount());
        pointHistoryRepository.save(PointHistory.of(
                wallet, PointHistoryType.EXCHANGE_REFUND, exchange.getAmount(),
                PointReferenceType.EXCHANGE, exchange.getId()));
        return wallet;
    }

    private PointWallet findWallet(Long userId) {
        return pointWalletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.WALLET_NOT_FOUND));
    }
}
