package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 DB 락이 동작해야 하므로 @Transactional 없이 실행하고, 끝나면 직접 데이터를 지운다.
 */
@SpringBootTest
class PointConcurrencyTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointWalletRepository pointWalletRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAllInBatch();
        pointWalletRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("한 지갑에 동시에 홀딩 요청이 몰려도 잔액을 넘겨서 홀딩되지 않는다")
    void concurrentHoldOnSameWallet() throws Exception {
        saveWallet(1L, 10_000L);
        int threadCount = 20;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();

        runConcurrently(threadCount, index -> () -> {
            try {
                pointService.hold(1L, 1_000L, (long) index);
                success.incrementAndGet();
            } catch (BusinessException e) {
                insufficient.incrementAndGet();
            }
            return null;
        });

        PointWallet wallet = pointWalletRepository.findByUserId(1L).orElseThrow();
        assertThat(success.get()).isEqualTo(10);
        assertThat(insufficient.get()).isEqualTo(10);
        assertThat(wallet.getBalance()).isZero();
        assertThat(wallet.getHeldBalance()).isEqualTo(10_000L);
        assertThat(pointHistoryRepository.count()).isEqualTo(10);
    }

    @Test
    @DisplayName("두 사람이 서로를 동시에 역전해도 데드락 없이 모두 처리되고 총 포인트는 보존된다")
    void crossReplaceHoldWithoutDeadlock() throws Exception {
        saveWallet(1L, 100_000L);
        saveWallet(2L, 100_000L);
        pointService.hold(1L, 10_000L, 0L);
        pointService.hold(2L, 10_000L, 0L);
        int rounds = 30;

        // 짝수 스레드: 1번이 2번을 역전 / 홀수 스레드: 2번이 1번을 역전
        runConcurrently(rounds * 2, index -> () -> {
            if (index % 2 == 0) {
                pointService.replaceHold(1L, 1_000L, 2L, 1_000L, (long) index);
            } else {
                pointService.replaceHold(2L, 1_000L, 1L, 1_000L, (long) index);
            }
            return null;
        });

        PointWallet first = pointWalletRepository.findByUserId(1L).orElseThrow();
        PointWallet second = pointWalletRepository.findByUserId(2L).orElseThrow();
        assertThat(first.getHeldBalance()).isEqualTo(10_000L);
        assertThat(second.getHeldBalance()).isEqualTo(10_000L);
        assertThat(first.getBalance() + first.getHeldBalance()).isEqualTo(100_000L);
        assertThat(second.getBalance() + second.getHeldBalance()).isEqualTo(100_000L);
    }

    private void saveWallet(Long userId, long balance) {
        PointWallet wallet = PointWallet.create(userId);
        wallet.charge(balance);
        pointWalletRepository.save(wallet);
    }

    /**
     * 모든 스레드가 준비될 때까지 기다렸다가 동시에 출발시킨다.
     * 작업 중 예상하지 못한 예외(데드락, 락 타임아웃 등)가 나면 future.get()에서 테스트가 실패한다.
     */
    private void runConcurrently(int taskCount, java.util.function.IntFunction<Callable<Void>> taskFactory) throws Exception {
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
            executor.shutdown();
        }
    }
}