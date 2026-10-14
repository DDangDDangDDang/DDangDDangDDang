package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 존재 확인(existsByAuctionId)을 동시에 통과해도 유니크 제약 위반이 500이 아니라 ORDER_002로 바뀌는지 확인한다.
 */
@SpringBootTest
class OrderCreateConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("같은 경매로 주문 생성이 동시에 들어오면 하나만 성공하고 나머지는 ORDER_ALREADY_EXISTS")
    void concurrentCreate() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        List<Object> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    results.add(orderService.createOrder(
                            new OrderCreateCommand(100L, 1L, 2L, "아이패드", null, 500_000L)));
                } catch (BusinessException e) {
                    results.add(e.getErrorCode());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(results).hasSize(threadCount);
        assertThat(results).filteredOn(r -> r instanceof Long).hasSize(1);
        assertThat(results).filteredOn(r -> r == OrderErrorCode.ORDER_ALREADY_EXISTS).hasSize(threadCount - 1);
        assertThat(orderRepository.count()).isEqualTo(1);
    }
}