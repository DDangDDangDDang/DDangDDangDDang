package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.point.entity.PointChargeType;
import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.infra.PaymentGateway;
import com.example.ddangddangddang.domain.point.infra.PaymentInfo;
import com.example.ddangddangddang.domain.point.infra.PaymentStatus;
import com.example.ddangddangddang.domain.point.repository.PointChargeRepository;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 같은 결제의 확인 요청이 동시에 여러 번 와도(프론트 확인 + 포트원 웹훅, 웹훅 재전송) 포인트가 한 번만 지급되는지 실제 MySQL에서 확인한다.
 * 포트원은 가짜로 바꿔 외부 서비스를 부르지 않는다.
 * 실제 DB 락이 동작해야 하므로 @Transactional 없이 실행하고, 끝나면 직접 데이터를 지운다.
 */
class PointChargeConcurrencyTest extends MySqlIntegrationTest {

    private static final long USER_ID = 7001L;
    private static final long AMOUNT = 10_000L;
    private static final int REQUESTS = 20;

    @Autowired
    private PointChargeService pointChargeService;

    @Autowired
    private PointChargeRepository pointChargeRepository;

    @Autowired
    private PointWalletRepository pointWalletRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAllInBatch();
        pointChargeRepository.deleteAllInBatch();
        pointWalletRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("프론트 확인과 웹훅이 동시에 20번 와도 포인트는 한 번만 지급되고 충전 이력도 하나만 남는다")
    void paidOnlyOnce() throws Exception {
        String paymentId = pendingCharge();
        given(paymentGateway.getPayment(paymentId)).willReturn(new PaymentInfo(paymentId, PaymentStatus.PAID, AMOUNT));

        // 짝수: 프론트 확인(본인 확인 경로), 홀수: 웹훅 경로
        runConcurrently(REQUESTS, index -> () -> {
            if (index % 2 == 0) {
                pointChargeService.completeCharge(USER_ID, paymentId);
            } else {
                pointChargeService.syncChargeFromWebhook(paymentId);
            }
            return null;
        });

        PointWallet wallet = pointWalletRepository.findByUserId(USER_ID).orElseThrow();
        PointCharge charge = pointChargeRepository.findByPaymentId(paymentId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(AMOUNT);
        assertThat(charge.getStatus()).isEqualTo(PointChargeType.COMPLETE);
        assertThat(chargeHistories(PointHistoryType.CHARGE)).hasSize(1);
    }

    @Test
    @DisplayName("같은 웹훅만 동시에 20번 재전송돼도 포인트는 한 번만 지급된다")
    void webhookRetriesPaidOnlyOnce() throws Exception {
        String paymentId = pendingCharge();
        given(paymentGateway.getPayment(paymentId)).willReturn(new PaymentInfo(paymentId, PaymentStatus.PAID, AMOUNT));

        runConcurrently(REQUESTS, index -> () -> {
            pointChargeService.syncChargeFromWebhook(paymentId);
            return null;
        });

        assertThat(pointWalletRepository.findByUserId(USER_ID).orElseThrow().getBalance()).isEqualTo(AMOUNT);
        assertThat(chargeHistories(PointHistoryType.CHARGE)).hasSize(1);
    }

    @Test
    @DisplayName("결제 금액이 다르면 동시에 몇 번이 와도 포인트를 주지 않고 실패로 끝나며, 결제 취소를 요청한다")
    void amountMismatchNeverPaid() throws Exception {
        String paymentId = pendingCharge();
        given(paymentGateway.getPayment(paymentId)).willReturn(new PaymentInfo(paymentId, PaymentStatus.PAID, AMOUNT + 1_000L));

        runConcurrently(REQUESTS, index -> () -> {
            if (index % 2 == 0) {
                pointChargeService.completeCharge(USER_ID, paymentId);
            } else {
                pointChargeService.syncChargeFromWebhook(paymentId);
            }
            return null;
        });

        assertThat(pointWalletRepository.findByUserId(USER_ID).orElseThrow().getBalance()).isZero();
        assertThat(pointChargeRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo(PointChargeType.FAILED);
        assertThat(chargeHistories(PointHistoryType.CHARGE)).isEmpty();
        verify(paymentGateway, atLeastOnce()).cancelPayment(anyString(), anyString());
    }

    @Test
    @DisplayName("지급 전에 부분 취소된 결제의 확인이 동시에 20번 와도, 한 번만 지급하고 취소분도 한 번만 회수한다")
    void partiallyCancelledOnlyOnce() throws Exception {
        String paymentId = pendingCharge();
        given(paymentGateway.getPayment(paymentId))
                .willReturn(new PaymentInfo(paymentId, PaymentStatus.PARTIAL_CANCELLED, AMOUNT, 3_000L));

        runConcurrently(REQUESTS, index -> () -> {
            if (index % 2 == 0) {
                pointChargeService.completeCharge(USER_ID, paymentId);
            } else {
                pointChargeService.syncChargeFromWebhook(paymentId);
            }
            return null;
        });

        PointCharge charge = pointChargeRepository.findByPaymentId(paymentId).orElseThrow();
        assertThat(pointWalletRepository.findByUserId(USER_ID).orElseThrow().getBalance()).isEqualTo(AMOUNT - 3_000L);
        assertThat(charge.getStatus()).isEqualTo(PointChargeType.COMPLETE);
        assertThat(charge.getCancelledAmount()).isEqualTo(3_000L);
        assertThat(chargeHistories(PointHistoryType.CHARGE)).hasSize(1);
        assertThat(chargeHistories(PointHistoryType.CHARGE_CANCEL)).hasSize(1);
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
    }

    @Test
    @DisplayName("결제가 실패했으면 동시에 몇 번이 와도 포인트를 주지 않고 결제 취소도 요청하지 않는다")
    void failedPaymentNeverPaid() throws Exception {
        String paymentId = pendingCharge();
        given(paymentGateway.getPayment(paymentId)).willReturn(new PaymentInfo(paymentId, PaymentStatus.FAILED, AMOUNT));

        runConcurrently(REQUESTS, index -> () -> {
            pointChargeService.syncChargeFromWebhook(paymentId);
            return null;
        });

        assertThat(pointWalletRepository.findByUserId(USER_ID).orElseThrow().getBalance()).isZero();
        assertThat(pointChargeRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo(PointChargeType.FAILED);
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
    }

    /** 지갑과 PENDING 충전 요청을 만들고 결제 ID를 돌려준다 */
    private String pendingCharge() {
        pointWalletRepository.save(PointWallet.create(USER_ID));
        String paymentId = "charge-" + UUID.randomUUID().toString().replace("-", "");
        pointChargeRepository.save(PointCharge.create(USER_ID, AMOUNT, paymentId));
        return paymentId;
    }

    private List<PointHistory> chargeHistories(PointHistoryType type) {
        return pointHistoryRepository.findAll().stream().filter(history -> history.getType() == type).toList();
    }

    /**
     * 모든 스레드가 준비될 때까지 기다렸다가 동시에 출발시킨다.
     * 작업 중 예상하지 못한 예외(데드락, 락 타임아웃 등)가 나면 future.get()에서 테스트가 실패한다.
     */
    private void runConcurrently(int taskCount, IntFunction<Callable<Void>> taskFactory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(taskCount, 16));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < taskCount; i++) {
                Callable<Void> task = taskFactory.apply(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
