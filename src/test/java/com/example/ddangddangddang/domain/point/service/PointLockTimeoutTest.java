package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 다른 트랜잭션이 지갑 락을 오래 쥐고 있을 때, 무한정 기다리지 않고 설정한 시간(3초) 안에 포기하는지 확인한다.
 * 락 대기 동작은 DB마다 달라서 실제 MySQL에서 확인한다.
 */
class PointLockTimeoutTest extends MySqlIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private PointService pointService;

    @Autowired
    private PointWalletRepository pointWalletRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAllInBatch();
        pointWalletRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("지갑 락을 다른 트랜잭션이 쥐고 있으면 3초 안팎에서 대기를 포기하고, 아무것도 반영되지 않는다")
    void giveUpAfterLockWaitTimeout() throws Exception {
        PointWallet wallet = PointWallet.create(USER_ID);
        wallet.charge(10_000L);
        pointWalletRepository.save(wallet);

        // 다른 트랜잭션이 지갑 락을 잡고 10초 동안 놓지 않는다
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    pointWalletRepository.findByUserIdForUpdate(USER_ID);
                    locked.countDown();
                    awaitQuietly(release, 10);
                }));
        locked.await();

        long start = System.nanoTime();
        Throwable thrown = catchThrowable(() -> pointService.hold(USER_ID, 1_000L, 1L));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        release.countDown();
        holder.get(15, TimeUnit.SECONDS);

        assertThat(thrown).isInstanceOf(PessimisticLockingFailureException.class);
        // 락 보유 시간(10초)이 아니라 설정한 대기 시간(3초) 근처에서 포기해야 한다
        assertThat(elapsed).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(6));

        PointWallet after = pointWalletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(after.getBalance()).isEqualTo(10_000L);
        assertThat(after.getHeldBalance()).isZero();
        assertThat(pointHistoryRepository.count()).isZero();
    }

    private static void awaitQuietly(CountDownLatch latch, long seconds) {
        try {
            latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}