package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 발송 후 7일 지났는데 도 구매 확정이 안 된 주문을 찾아 자동 확정
 * 주문 하나씩 따로 처리해, 한 건이 실패해도 나머지는 처리 후 실패 한 건은 다음 주기에 다시 시도
 * 실패한 주문은 횟수를 세고, 정해진횟수를 넘으면 대상에서 빼고 관리자 확인이 필요하다고 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoConfirmScheduler {
    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${order.auto-confirm.delay-ms:60000}")
    public void confirmOverdueOrders(){
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(OrderService.AUTO_CONFIRM_DAYS);
        LocalDateTime retryAfter = now.minusHours(OrderService.AUTO_CONFIRM_RETRY_HOURS);
        List<Long> orderIds = orderRepository.findConfirmOverdueIds(OrderStatus.SHIPPING, cutoff,OrderService.MAX_AUTO_CONFIRM_FAILURES, retryAfter, PageRequest.of(0, BATCH_SIZE));
        for (Long orderId : orderIds){
            try {
                orderService.autoConfirm(orderId);
            }catch (Exception e){
                recordFailure(orderId, e);
            }
        }
    }

    //실패 횟수를 올리고, 횟수를 넘기면 관리자 확인이 필요하다고 눈에 띄게 남긴다
    private void recordFailure(Long orderId, Exception cause){
        try {
            int failCount = orderService.recordAutoConfirmFailure(orderId);
            if (failCount >= OrderService.MAX_AUTO_CONFIRM_FAILURES){
                log.error("[관리자 확인 필요] 자동 구매 확정이 {}번 실패해 대상에서 제외했습니다. orderId={}",failCount,orderId,cause);
            }else {
                log.warn("자동 구매 확정 실패 ({}/{}) orderId={}",failCount, OrderService.MAX_AUTO_CONFIRM_FAILURES,orderId,cause);
            }
        } catch (Exception e){
            log.error("자동 구매 확정 실패 횟수 기록 중 오류 orderId={}",orderId,e);
        }
    }
}
