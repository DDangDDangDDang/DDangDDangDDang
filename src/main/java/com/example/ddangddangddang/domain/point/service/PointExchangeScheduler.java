package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointExchangeType;
import com.example.ddangddangddang.domain.point.infra.PayoutClient;
import com.example.ddangddangddang.domain.point.infra.PayoutRequest;
import com.example.ddangddangddang.domain.point.infra.PayoutResult;
import com.example.ddangddangddang.domain.point.infra.PayoutStatus;
import com.example.ddangddangddang.domain.point.repository.PointExchangeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 환전 송금을 비동기로 처리한다.
 *
 * 1. processPending: PENDING 건을 가져가 송금한다.
 * 2. recoverStuck: 결과를 모른 채 PROCESSING에 오래 머문 건을 송금사에 다시 조회해 확정한다.
 *    송금사에 기록이 없으면(요청이 도착하지 않음) 같은 payoutId로 다시 요청한다. 멱등 키라 두 번 송금되지 않는다.
 *
 * 서버가 여러 대여도 startProcessing의 행 락 덕분에 같은 건이 두 번 송금되지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "point.exchange.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PointExchangeScheduler {

    private static final int BATCH_SIZE = 50;

    private final PointExchangeRepository pointExchangeRepository;
    private final PointExchangeProcessor pointExchangeProcessor;
    private final PayoutClient payoutClient;
    private final Duration stuckThreshold;

    public PointExchangeScheduler(PointExchangeRepository pointExchangeRepository,
                                  PointExchangeProcessor pointExchangeProcessor,
                                  PayoutClient payoutClient,
                                  @Value("${point.exchange.worker.stuck-threshold-minutes:10}") long stuckThresholdMinutes) {
        this.pointExchangeRepository = pointExchangeRepository;
        this.pointExchangeProcessor = pointExchangeProcessor;
        this.payoutClient = payoutClient;
        this.stuckThreshold = Duration.ofMinutes(stuckThresholdMinutes);
    }

    @Scheduled(fixedDelayString = "${point.exchange.worker.pending-delay-ms:10000}")
    public void processPending() {
        List<Long> ids = pointExchangeRepository.findIdsByStatus(
                PointExchangeType.PENDING, PageRequest.of(0, BATCH_SIZE));

        for (Long id : ids) {
            try {
                processOne(id);
            } catch (Exception e) {
                // 한 건이 실패해도 나머지는 계속 처리한다. 해당 건은 다음 주기나 복구 스케줄러가 처리한다
                log.error("환전 송금 처리 중 오류 exchangeId={}", id, e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${point.exchange.worker.recovery-delay-ms:60000}")
    public void recoverStuck() {
        LocalDateTime before = LocalDateTime.now().minus(stuckThreshold);
        List<Long> ids = pointExchangeRepository.findIdsByStatusAndProcessingStartedAtBefore(
                PointExchangeType.PROCESSING, before, PageRequest.of(0, BATCH_SIZE));

        for (Long id : ids) {
            try {
                recoverOne(id);
            } catch (Exception e) {
                log.error("환전 복구 처리 중 오류 exchangeId={}", id, e);
            }
        }
    }

    void processOne(Long exchangeId) {
        Optional<PayoutRequest> request = pointExchangeProcessor.startProcessing(exchangeId, LocalDateTime.now());
        if (request.isEmpty()) {
            return;
        }

        PayoutResult result = requestPayoutSafely(request.get());
        pointExchangeProcessor.applyResult(exchangeId, result);
    }

    void recoverOne(Long exchangeId) {
        Optional<PayoutRequest> request = pointExchangeProcessor.findProcessing(exchangeId);
        if (request.isEmpty()) {
            return;
        }

        PayoutResult result = getPayoutSafely(request.get().payoutId());
        if (result.status() == PayoutStatus.NOT_FOUND) {
            log.info("송금사에 요청 기록 없음, 같은 payoutId로 재요청 exchangeId={}", exchangeId);
            result = requestPayoutSafely(request.get());
        }
        pointExchangeProcessor.applyResult(exchangeId, result);
    }

    /** 예외가 나면 결과를 모르는 것으로 본다. 실패로 보면 실제로 송금됐을 때 돈과 포인트를 둘 다 주게 된다 */
    private PayoutResult requestPayoutSafely(PayoutRequest request) {
        try {
            return payoutClient.requestPayout(request);
        } catch (Exception e) {
            log.warn("송금 요청 중 예외, 결과 미확정으로 처리 payoutId={}", request.payoutId(), e);
            return PayoutResult.inProgress();
        }
    }

    private PayoutResult getPayoutSafely(String payoutId) {
        try {
            return payoutClient.getPayout(payoutId);
        } catch (Exception e) {
            log.warn("송금 조회 중 예외, 결과 미확정으로 처리 payoutId={}", payoutId, e);
            return PayoutResult.inProgress();
        }
    }
}
