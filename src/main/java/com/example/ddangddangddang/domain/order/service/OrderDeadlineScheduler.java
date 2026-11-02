package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배송지 입력 기한·발송 기한을 넘긴 주문을 찾아 취소한다.
 * 취소는 건별 트랜잭션이라 한 건이 실패해도 나머지는 처리되고, 실패한 주문은 다음 주기에 다시 시도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDeadlineScheduler {

    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${order.deadline.delay-ms:60000}")
    public void cancelOverdueOrders() {
        LocalDateTime now = LocalDateTime.now();
        PageRequest batch = PageRequest.of(0, BATCH_SIZE);
        cancelAll(orderRepository.findAddressOverdueIds(now, batch), OrderCancelReason.BUYER_NO_ADDRESS);
        cancelAll(orderRepository.findShipOverdueIds(now, batch), OrderCancelReason.SELLER_NOT_SHIPPED);
    }

    private void cancelAll(List<Long> orderIds, OrderCancelReason reason) {
        for (Long orderId : orderIds) {
            try {
                orderService.cancelOverdue(orderId, reason);
            } catch (Exception e) {
                log.error("기한 초과 주문 취소 중 오류 orderId={}", orderId, e);
            }
        }
    }
}
