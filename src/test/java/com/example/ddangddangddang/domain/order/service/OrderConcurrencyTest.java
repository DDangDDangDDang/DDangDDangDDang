package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
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
class OrderConcurrencyTest {

    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PointWalletRepository pointWalletRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAllInBatch();
        pointWalletRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("구매 확정 요청이 동시에 여러 번 와도 판매자 정산은 한 번만 된다")
    void concurrentConfirm() throws Exception {
        pointWalletRepository.save(PointWallet.create(SELLER_ID));
        Order order = Order.create(100L, BUYER_ID, SELLER_ID, "아이패드", null, 500_000L);
        order.ship("1234567890");
        Long orderId = orderRepository.save(order).getId();

        int threadCount = 10;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    orderService.confirmPurchase(BUYER_ID, orderId);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        PointWallet sellerWallet = pointWalletRepository.findByUserId(SELLER_ID).orElseThrow();
        long settleCount = pointHistoryRepository.findAll().stream()
                .filter(history -> history.getType() == PointHistoryType.SETTLE)
                .count();
        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threadCount - 1);
        assertThat(sellerWallet.getBalance()).isEqualTo(500_000L);
        assertThat(settleCount).isEqualTo(1);
    }
}