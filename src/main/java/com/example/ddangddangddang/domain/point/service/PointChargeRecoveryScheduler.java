package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.point.entity.PointChargeType;
import com.example.ddangddangddang.domain.point.repository.PointChargeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 확인 보정. 충전은 프론트의 완료 호출이나 포트원 웹훅으로 확정되는데, 둘 다 오지 않으면
 * (결제 직후 창을 닫음, 네트워크 끊김, 웹훅 설정 오류) 돈은 빠져나갔는데 포인트가 들어오지 않은 채 PENDING으로 남는다.
 * 그런 충전을 주기적으로 포트원에 다시 조회해 반영한다.
 *
 * - 방금 만든 충전(결제창에서 결제 중)과 겹치지 않도록 minAge보다 오래된 것만 본다.
 * - maxAge보다 오래된 충전은 보지 않는다. (결제하지 않고 버려진 요청을 계속 조회하지 않게)
 * - 반영은 PointChargeProcessor가 충전 요청 행을 잠그고 PENDING인지 다시 확인하므로, 프론트·웹훅과 동시에 와도 한 번만 지급된다.
 *
 * 같은 주기에 결제 취소 때 다 회수하지 못한 포인트도 마저 회수한다. (입찰이 풀리거나 포인트가 들어와 사용 가능 포인트가 생긴 경우)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "point.charge.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PointChargeRecoveryScheduler {

    private static final int BATCH_SIZE = 50;

    private final PointChargeRepository pointChargeRepository;
    private final PointChargeService pointChargeService;
    private final PointChargeProcessor pointChargeProcessor;
    private final Duration minAge;
    private final Duration maxAge;

    public PointChargeRecoveryScheduler(PointChargeRepository pointChargeRepository,
                                        PointChargeService pointChargeService,
                                        PointChargeProcessor pointChargeProcessor,
                                        @Value("${point.charge.recovery.min-age-minutes:10}") long minAgeMinutes,
                                        @Value("${point.charge.recovery.max-age-hours:24}") long maxAgeHours) {
        this.pointChargeRepository = pointChargeRepository;
        this.pointChargeService = pointChargeService;
        this.pointChargeProcessor = pointChargeProcessor;
        this.minAge = Duration.ofMinutes(minAgeMinutes);
        this.maxAge = Duration.ofHours(maxAgeHours);
    }

    @Scheduled(fixedDelayString = "${point.charge.recovery.delay-ms:300000}")
    public void run() {
        recoverPending();
        collectUnreclaimed();
    }

    void recoverPending() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minus(maxAge);
        LocalDateTime to = now.minus(minAge);
        // 결제하지 않고 버려진 요청이 앞을 막지 않도록 ID 순으로 끝까지 넘긴다.
        Long afterId = 0L;
        while (true) {
            List<PointCharge> charges = pointChargeRepository.findCreatedBetween(
                    PointChargeType.PENDING, from, to, afterId, PageRequest.of(0, BATCH_SIZE));
            for (PointCharge charge : charges) {
                try {
                    pointChargeService.recoverPending(charge.getPaymentId());
                } catch (Exception e) {
                    // 한 건이 실패해도(포트원 오류 등) 나머지는 계속한다. 실패한 건은 다음 주기에 다시 본다.
                    log.warn("충전 결제 확인 보정 실패 paymentId={}", charge.getPaymentId(), e);
                }
            }
            if (charges.size() < BATCH_SIZE) {
                return;
            }
            afterId = charges.get(charges.size() - 1).getId();
        }
    }

    void collectUnreclaimed() {
        Long afterId = 0L;
        while (true) {
            List<PointCharge> charges = pointChargeRepository.findUnreclaimed(afterId, PageRequest.of(0, BATCH_SIZE));
            for (PointCharge charge : charges) {
                try {
                    pointChargeProcessor.collectUnreclaimed(charge.getPaymentId());
                } catch (Exception e) {
                    log.warn("남겨 둔 결제 취소분 회수 실패 paymentId={}", charge.getPaymentId(), e);
                }
            }
            if (charges.size() < BATCH_SIZE) {
                return;
            }
            afterId = charges.get(charges.size() - 1).getId();
        }
    }
}
