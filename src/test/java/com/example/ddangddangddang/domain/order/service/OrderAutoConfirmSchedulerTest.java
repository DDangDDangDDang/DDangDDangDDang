package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderAutoConfirmSchedulerTest {

    @InjectMocks
    private OrderAutoConfirmScheduler scheduler;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderTrackingService orderTrackingService;

    @Test
    @DisplayName("한 주문이 실패해도 다음 주문은 처리하고, 실패한 주문은 횟수를 기록한다")
    void failure_recordsCountAndContinues() {
        givenOverdueOrders(1L, 2L);
        given(orderService.autoConfirm(eq(1L), any())).willThrow(new IllegalStateException("정산 실패"));
        given(orderService.recordAutoConfirmFailure(1L)).willReturn(1);

        scheduler.confirmOverdueOrders();

        verify(orderService).recordAutoConfirmFailure(1L);
        verify(orderService).autoConfirm(eq(2L), any());
        verify(orderService, never()).recordAutoConfirmFailure(2L);
    }

    @Test
    @DisplayName("실패 횟수 기록마저 실패해도 스케줄러는 멈추지 않고 다음 주문을 처리한다")
    void recordFailureFails_doesNotStop() {
        givenOverdueOrders(1L, 2L);
        given(orderService.autoConfirm(eq(1L), any())).willThrow(new IllegalStateException("정산 실패"));
        given(orderService.recordAutoConfirmFailure(1L)).willThrow(new IllegalStateException("DB 오류"));

        scheduler.confirmOverdueOrders();

        verify(orderService).autoConfirm(eq(2L), any());
    }

    private void givenOverdueOrders(Long... orderIds) {
        given(orderRepository.findConfirmOverdueIds(eq(OrderStatus.SHIPPING), any(), any(),
                eq(OrderService.MAX_AUTO_CONFIRM_FAILURES), any(), any()))
                .willReturn(List.of(orderIds));
    }
}