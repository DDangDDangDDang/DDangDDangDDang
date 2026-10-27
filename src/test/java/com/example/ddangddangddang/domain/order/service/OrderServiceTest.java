package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.dto.OrderAddressRequest;
import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.dto.OrderResponse;
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.event.OrderConfirmedEvent;
import com.example.ddangddangddang.domain.order.entity.OrderRole;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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

    @Mock
    private PointService pointService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {

        private final OrderCreateCommand command =
                new OrderCreateCommand(100L, BUYER_ID, SELLER_ID, "아이패드", "https://img/1.png", 500_000L);

        @Test
        @DisplayName("ESCROW 상태로 주문을 만들고 주문 ID를 반환한다")
        void success() {
            given(orderRepository.existsByAuctionId(100L)).willReturn(false);
            given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(invocation -> {
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
            verify(orderRepository, never()).saveAndFlush(any());
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

    @Nested
    @DisplayName("배송지 입력")
    class RegisterDeliveryAddress {

        private final OrderAddressRequest request =
                new OrderAddressRequest("홍길동", "01012345678", "서울시 강남구 테헤란로 1", "문 앞");

        @Test
        @DisplayName("구매자가 배송지를 입력한다")
        void success() {
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order(10L)));

            OrderResponse response = orderService.registerDeliveryAddress(BUYER_ID, 10L, request);

            assertThat(response.receiverName()).isEqualTo("홍길동");
            assertThat(response.address()).isEqualTo("서울시 강남구 테헤란로 1");
            assertThat(response.deliveryMemo()).isEqualTo("문 앞");
        }

        @Test
        @DisplayName("구매자가 아니면 예외가 발생한다")
        void notBuyer() {
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order(10L)));

            assertThatThrownBy(() -> orderService.registerDeliveryAddress(SELLER_ID, 10L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.NOT_ORDER_BUYER_ADDRESS);
        }
    }

    @Nested
    @DisplayName("발송 처리")
    class Ship {

        @Test
        @DisplayName("판매자가 택배사와 송장번호를 입력하면 SHIPPING이 된다")
        void success() {
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(shippableOrder(10L)));

            OrderResponse response = orderService.ship(SELLER_ID, 10L, Courier.CJ, "1234567890");

            assertThat(response.status()).isEqualTo(OrderStatus.SHIPPING);
            assertThat(response.courier()).isEqualTo(Courier.CJ);
            assertThat(response.trackingNo()).isEqualTo("1234567890");
            verify(orderRepository).flush();
        }

        @Test
        @DisplayName("판매자가 아니면 예외가 발생한다")
        void notSeller() {
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(shippableOrder(10L)));

            assertThatThrownBy(() -> orderService.ship(BUYER_ID, 10L, Courier.CJ, "1234567890"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.NOT_ORDER_SELLER);
        }

        @Test
        @DisplayName("같은 송장이 다른 주문에 있으면 발송하지 않는다")
        void duplicateTrackingNo() {
            Order order = shippableOrder(10L);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
            given(orderRepository.existsByCourierAndTrackingNo(Courier.CJ, "1234567890")).willReturn(true);

            assertThatThrownBy(() -> orderService.ship(SELLER_ID, 10L, Courier.CJ, "1234567890"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.DUPLICATE_TRACKING_NO);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.ESCROW);
        }

        @Test
        @DisplayName("동시에 등록돼 유니크 제약에 걸리면 DUPLICATE_TRACKING_NO로 바꾼다")
        void uniqueViolationOnFlush() {
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(shippableOrder(10L)));
            willThrow(new DataIntegrityViolationException("duplicate",
                    new ConstraintViolationException("duplicate", null, "orders." + Order.UK_COURIER_TRACKING_NO)))
                    .given(orderRepository).flush();

            assertThatThrownBy(() -> orderService.ship(SELLER_ID, 10L, Courier.CJ, "1234567890"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.DUPLICATE_TRACKING_NO);
        }

        @Test
        @DisplayName("다른 제약 위반은 그대로 던진다")
        void otherViolationOnFlush() {
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(shippableOrder(10L)));
            willThrow(new DataIntegrityViolationException("other",
                    new ConstraintViolationException("other", null, "orders.uk_something_else")))
                    .given(orderRepository).flush();

            assertThatThrownBy(() -> orderService.ship(SELLER_ID, 10L, Courier.CJ, "1234567890"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("구매 확정")
    class ConfirmPurchase {

        @Test
        @DisplayName("구매자가 확정하면 COMPLETED가 되고 판매자에게 낙찰 금액을 정산하며, 도네이션 포인트 적립을 알린다")
        void success() {
            Order order = shippableOrder(10L);
            order.ship(Courier.CJ, "1234567890");
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            OrderResponse response = orderService.confirmPurchase(BUYER_ID, 10L);

            assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
            verify(pointService).settle(SELLER_ID, 500_000L, 10L);
            verify(eventPublisher).publishEvent(new OrderConfirmedEvent(10L, BUYER_ID, SELLER_ID, 500_000L));
        }

        @Test
        @DisplayName("구매자가 아니면 예외가 발생하고 정산하지 않는다")
        void notBuyer() {
            Order order = shippableOrder(10L);
            order.ship(Courier.CJ, "1234567890");
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.confirmPurchase(SELLER_ID, 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.NOT_ORDER_BUYER);
            verify(pointService, never()).settle(anyLong(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("발송 전이면 예외가 발생하고 정산하지 않는다")
        void beforeShip() {
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order(10L)));

            assertThatThrownBy(() -> orderService.confirmPurchase(BUYER_ID, 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.CANNOT_CONFIRM);
            verify(pointService, never()).settle(anyLong(), anyLong(), anyLong());
        }
    }

    private Order order(Long orderId) {
        Order order = Order.create(100L, BUYER_ID, SELLER_ID, "아이패드", "https://img/1.png", 500_000L);
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private Order shippableOrder(Long orderId) {
        Order order = order(orderId);
        order.registerDeliveryAddress("홍길동", "01012345678", "서울시 강남구 테헤란로 1", null);
        return order;
    }
}