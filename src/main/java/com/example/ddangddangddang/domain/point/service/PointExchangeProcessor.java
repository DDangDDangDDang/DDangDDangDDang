package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.infra.PayoutRequest;
import com.example.ddangddangddang.domain.point.infra.PayoutResult;
import com.example.ddangddangddang.domain.point.repository.PointExchangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 송금 처리의 DB 단계들. 각 메서드가 짧은 트랜잭션 하나다.
 * 외부 송금 호출은 여기서 하지 않는다. 락을 잡은 채로 외부 API를 기다리지 않기 위해서다.
 *
 * 흐름: startProcessing (트랜잭션) → 송금 API 호출 (트랜잭션 밖) → applyResult (트랜잭션)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointExchangeProcessor {

    private final PointExchangeRepository pointExchangeRepository;
    private final PointExchangeLedger pointExchangeLedger;

    /**
     * PENDING → PROCESSING. 성공하면 송금 요청에 필요한 정보를 돌려준다.
     * 이미 다른 워커가 가져갔거나 사용자가 취소했으면 빈 값을 돌려준다.
     */
    @Transactional
    public Optional<PayoutRequest> startProcessing(Long exchangeId, LocalDateTime now) {
        Optional<PointExchange> found = pointExchangeRepository.findByIdForUpdate(exchangeId);
        if (found.isEmpty() || !found.get().isPending()) {
            return Optional.empty();
        }

        PointExchange exchange = found.get();
        exchange.startProcessing(now);
        return Optional.of(toPayoutRequest(exchange));
    }

    /** 복구 스케줄러용. 아직 PROCESSING이면 송금 정보를 돌려준다 */
    @Transactional(readOnly = true)
    public Optional<PayoutRequest> findProcessing(Long exchangeId) {
        return pointExchangeRepository.findById(exchangeId)
                .filter(PointExchange::isProcessing)
                .map(this::toPayoutRequest);
    }

    /**
     * 송금 결과 반영.
     * SUCCESS → COMPLETE, FAILED → FAILED + 포인트 환불, 그 외(결과 모름) → PROCESSING 유지.
     */
    @Transactional
    public void applyResult(Long exchangeId, PayoutResult result) {
        PointExchange exchange = pointExchangeRepository.findByIdForUpdate(exchangeId).orElse(null);
        if (exchange == null || !exchange.isProcessing()) {
            return; // 이미 다른 경로에서 확정됨
        }

        switch (result.status()) {
            case SUCCESS -> exchange.complete();
            case FAILED -> {
                log.info("환전 송금 실패로 환불 exchangeId={}, reason={}", exchangeId, result.failReason());
                exchange.fail(result.failReason());
                pointExchangeLedger.refund(exchange);
            }
            case IN_PROGRESS, NOT_FOUND ->
                    log.info("환전 송금 결과 미확정, PROCESSING 유지 exchangeId={}", exchangeId);
        }
    }

    private PayoutRequest toPayoutRequest(PointExchange exchange) {
        return new PayoutRequest(
                exchange.getPayoutId(),
                exchange.getAmount(),
                exchange.getBankCode(),
                exchange.getAccountNumber(),
                exchange.getAccountHolder()
        );
    }
}
