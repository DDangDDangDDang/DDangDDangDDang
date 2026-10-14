package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.dto.OrderResponse;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderRole;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {

        private final OrderCreateCommand command =
                new OrderCreateCommand(100L, BUYER_ID, SELLER_ID, "아이패드", "https://img/1.png", 500_000L);

        @Test
        @DisplayName("ESCROW 상태로 주문을 만들고 주문 ID를 반환한다")
        void success() {
            given(orderRepository.existsByAuctionId(100L)).willReturn(false);
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 10L);
                assertThat(saved.getStatus()).isEqualTo(OrderStatus.ESCROW);
                assertThat(saved.getOrderNo()).isNotBlank();
                return saved;
            });

            Long orderId = orderService.createOrder(command);

            assertThat(orderId).isEqualTo(10L);
        }

        @Test
        @DisplayName("같은 경매로 주문이 이미 있으면 예외가 발생한다")
        void duplicated() {
            given(orderRepository.existsByAuctionId(100L)).willReturn(true);

            assertThatThrownBy(() -> orderService.createOrder(command))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ORDER_ALREADY_EXISTS);
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("주문 조회")
    class GetOrder {

        @Test
        @DisplayName("구매자와 판매자는 주문을 조회할 수 있다")
        void participant() {
            given(orderRepository.findById(10L)).willReturn(Optional.of(order(10L)));

            OrderResponse byBuyer = orderService.getOrder(BUYER_ID, 10L);
            OrderResponse bySeller = orderService.getOrder(SELLER_ID, 10L);

            assertThat(byBuyer.orderId()).isEqualTo(10L);
            assertThat(bySeller.orderId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("구매자도 판매자도 아니면 예외가 발생한다")
        void notParticipant() {
            given(orderRepository.findById(10L)).willReturn(Optional.of(order(10L)));

            assertThatThrownBy(() -> orderService.getOrder(99L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.NOT_ORDER_PARTICIPANT);
        }

        @Test
        @DisplayName("주문이 없으면 예외가 발생한다")
        void notFound() {
            given(orderRepository.findById(10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(BUYER_ID, 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("role이 SELLER면 판매 내역을 조회한다")
        void sellerOrders() {
            given(orderRepository.findSellerOrders(SELLER_ID, null, null, PageRequest.of(0, 21)))
                    .willReturn(List.of(order(10L)));

            var response = orderService.getOrders(SELLER_ID, OrderRole.SELLER, null, null, 20);

            assertThat(response.content()).hasSize(1);
            verify(orderRepository, never()).findBuyerOrders(any(), any(), any(), any());
        }
    }

    private Order order(Long orderId) {
        Order order = Order.create(100L, BUYER_ID, SELLER_ID, "아이패드", "https://img/1.png", 500_000L);
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }
}