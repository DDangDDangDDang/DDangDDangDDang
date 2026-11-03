package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 배송지 입력 기한·발송 기한을 넘긴 주문을 찾아 취소한다.
 * 취소는 건별 트랜잭션이라 한 건이 실패해도 나머지는 처리되고, 실패한 주문은 다음 주기에 다시 시도한다.
 * 주문 ID 순서로 끝까지 넘겨 가며 처리해서, 실패한 주문이 한 번에 가져오는 수만큼 쌓여도 뒤의 주문이 막히지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDeadlineScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.deadline.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${order.deadline.delay-ms:60000}")
    public void cancelOverdueOrders() {
        LocalDateTime now = LocalDateTime.now();
        cancelAll((afterId, batch) -> orderRepository.findAddressOverdueIds(now, afterId, batch), OrderCancelReason.BUYER_NO_ADDRESS);
        cancelAll((afterId, batch) -> orderRepository.findShipOverdueIds(now, afterId, batch), OrderCancelReason.SELLER_NOT_SHIPPED);
    }

    private void cancelAll(BiFunction<Long, PageRequest, List<Long>> fetch, OrderCancelReason reason) {
        PageRequest batch = PageRequest.of(0, batchSize);
        long afterId = 0;
        List<Long> orderIds;
        do {
            orderIds = fetch.apply(afterId, batch);
            cancelEach(orderIds, reason);
            if (!orderIds.isEmpty()) {
                afterId = orderIds.get(orderIds.size() - 1);
            }
        } while (orderIds.size() == batchSize);
    }

    private void cancelEach(List<Long> orderIds, OrderCancelReason reason) {
        for (Long orderId : orderIds) {
            try {
                orderService.cancelOverdue(orderId, reason);
            } catch (Exception e) {
                log.error("기한 초과 주문 취소 중 오류 orderId={}", orderId, e);
            }
        }
    }
}
