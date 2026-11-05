package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.address.service.UserAddressService;
import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livechat.moderation.ContentViolation;
import com.example.ddangddangddang.domain.order.commission.CommissionPolicy;
import com.example.ddangddangddang.domain.order.dto.OrderAddressRequest;
import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.dto.OrderResponse;
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.event.OrderDelayRespondedEvent;
import com.example.ddangddangddang.domain.order.event.OrderDelayRequestedEvent;
import com.example.ddangddangddang.domain.order.entity.OrderDelayStatus;
import com.example.ddangddangddang.domain.order.event.OrderConfirmedEvent;
import com.example.ddangddangddang.domain.order.entity.OrderRole;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.order.entity.OrderIssueType;
import com.example.ddangddangddang.domain.order.entity.OrderReviewReason;
import com.example.ddangddangddang.domain.order.entity.OrderReviewResult;
import com.example.ddangddangddang.domain.order.event.OrderCanceledEvent;
import com.example.ddangddangddang.domain.order.event.OrderReviewRequestedEvent;
import com.example.ddangddangddang.domain.order.event.OrderReviewResolvedEvent;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    private CommissionPolicy commissionPolicy;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ChatContentFilter contentFilter;

    @Mock
    private UserAddressService userAddressService;

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
            verify(userAddressService).saveAsDefaultIfNone(BUYER_ID,"홍길동","01012345678","서울시 강남구 테헤란로 1","문 앞");
        }

        @Test
        @DisplayName("구매자가 아니면 예외가 발생한다")
        void notBuyer() {
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order(10L)));

            assertThatThrownBy(() -> orderService.registerDeliveryAddress(SELLER_ID, 10L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.NOT_ORDER_BUYER_ADDRESS);
            verify(userAddressService,never()).saveAsDefaultIfNone(any(),any(),any(),any(),any());
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

    @Nested
    @DisplayName("자동 구매 확정")
    class AutoConfirm {

        @Test
        @DisplayName("발송 후 7일이 지나고 배송 완료가 확인되면 확정하고 판매자에게 정산하며, 도네이션 포인트 적립을 알린다")
        void delivered_confirmsAndSettles() {
            Order order = shippedOrder(10L, 8);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            boolean confirmed = orderService.autoConfirm(10L, TrackingCheck.DELIVERED);

            assertThat(confirmed).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(order.getDeliveredAt()).isNotNull();
            verify(pointService).settle(SELLER_ID, 500_000L, 10L);
            verify(eventPublisher).publishEvent(new OrderConfirmedEvent(10L, BUYER_ID, SELLER_ID, 500_000L));
        }

        @Test
        @DisplayName("발송 후 7일이 안 지났으면 건너뛰고 정산하지 않는다")
        void notYetOverdue_skips() {
            Order order = shippedOrder(10L, 3);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            assertThat(orderService.autoConfirm(10L, TrackingCheck.DELIVERED)).isFalse();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
            verify(pointService, never()).settle(anyLong(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("그 사이 구매자가 직접 확정했으면 건너뛰어 정산이 두 번 되지 않는다")
        void alreadyConfirmed_skips() {
            Order order = shippedOrder(10L, 8);
            order.confirm();
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            assertThat(orderService.autoConfirm(10L, TrackingCheck.DELIVERED)).isFalse();
            verify(pointService, never()).settle(anyLong(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("발송 후 7일이 지났는데 송장이 조회되지 않으면(가짜 송장 가능성) 정산하지 않고 관리자 확인으로 넘긴다")
        void notFound_requestsReview() {
            Order order = shippedOrder(10L, 8);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            assertThat(orderService.autoConfirm(10L, TrackingCheck.NOT_FOUND)).isFalse();

            assertThat(order.getReviewReason()).isEqualTo(OrderReviewReason.TRACKING_NOT_FOUND);
            assertThat(order.isUnderReview()).isTrue();
            verify(pointService, never()).settle(anyLong(), anyLong(), anyLong());
            verify(eventPublisher).publishEvent(any(OrderReviewRequestedEvent.class));
        }

        @Test
        @DisplayName("아직 배송 중이면 6시간 뒤 다시 보고, 발송 후 14일이 지나도 배송 완료가 아니면 관리자 확인으로 넘긴다")
        void inTransit_postponesThenReview() {
            Order early = shippedOrder(10L, 8);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(early));
            assertThat(orderService.autoConfirm(10L, TrackingCheck.IN_TRANSIT)).isFalse();
            assertThat(early.getReviewReason()).isNull();
            assertThat(early.getAutoConfirmNextCheckAt()).isAfter(LocalDateTime.now().plusHours(5));

            Order late = shippedOrder(11L, 15);
            given(orderRepository.findByIdForUpdate(11L)).willReturn(Optional.of(late));
            assertThat(orderService.autoConfirm(11L, TrackingCheck.IN_TRANSIT)).isFalse();
            assertThat(late.getReviewReason()).isEqualTo(OrderReviewReason.TRACKING_NOT_DELIVERED);
            verify(pointService, never()).settle(anyLong(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("조회 서비스 장애면 미루고, 발송 후 14일이 지나도 확인하지 못하면 관리자 확인으로 넘긴다")
        void unavailable_postponesThenReview() {
            Order early = shippedOrder(10L, 8);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(early));
            assertThat(orderService.autoConfirm(10L, TrackingCheck.UNAVAILABLE)).isFalse();
            assertThat(early.getReviewReason()).isNull();
            assertThat(early.getAutoConfirmNextCheckAt()).isNotNull();

            Order late = shippedOrder(11L, 15);
            given(orderRepository.findByIdForUpdate(11L)).willReturn(Optional.of(late));
            orderService.autoConfirm(11L, TrackingCheck.UNAVAILABLE);
            assertThat(late.getReviewReason()).isEqualTo(OrderReviewReason.TRACKING_UNAVAILABLE);
        }

        @Test
        @DisplayName("조회할 수 없는 택배사(기타)는 발송 후 14일이 지나야 확정하고, 그 전에는 14일째로 미룬다")
        void unsupportedCourier_confirmsAfter14Days() {
            Order early = shippedOrder(10L, 8);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(early));
            assertThat(orderService.autoConfirm(10L, TrackingCheck.UNSUPPORTED)).isFalse();
            assertThat(early.getAutoConfirmNextCheckAt()).isEqualTo(early.getShippedAt().plusDays(14));

            Order late = shippedOrder(11L, 14);
            given(orderRepository.findByIdForUpdate(11L)).willReturn(Optional.of(late));
            assertThat(orderService.autoConfirm(11L, TrackingCheck.UNSUPPORTED)).isTrue();
            assertThat(late.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("관리자 확인 대기 중인 주문은 배송 완료여도 자동 확정하지 않는다")
        void underReview_skips() {
            Order order = shippedOrder(10L, 8);
            order.reportIssue(OrderIssueType.NOT_RECEIVED, null, LocalDateTime.now());
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            assertThat(orderService.autoConfirm(10L, TrackingCheck.DELIVERED)).isFalse();
            verify(pointService, never()).settle(anyLong(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("실패 횟수를 기록하면 1씩 오르고 실패 시각이 남는다")
        void recordFailure_increments() {
            Order order = shippedOrder(10L, 8);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            assertThat(orderService.recordAutoConfirmFailure(10L)).isEqualTo(1);
            assertThat(orderService.recordAutoConfirmFailure(10L)).isEqualTo(2);
            assertThat(order.getAutoConfirmLastFailedAt()).isNotNull();
        }

        // 발송한 주문을 만들고, 발송 시각을 며칠 전으로 돌린다
        private Order shippedOrder(Long orderId, int shippedDaysAgo) {
            Order order = shippableOrder(orderId);
            order.ship(Courier.CJ, "1234567890");
            ReflectionTestUtils.setField(order, "shippedAt", LocalDateTime.now().minusDays(shippedDaysAgo));
            return order;
        }
    }

    @Nested
    @DisplayName("구매자 문제 신고와 관리자 확인")
    class IssueReview {

        @Test
        @DisplayName("구매자가 신고하면 자동 확정이 멈추고 판매자에게 알린다. 다시 신고하면 ORDER_024")
        void report() {
            Order order = shipped(10L);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            OrderResponse response = orderService.reportIssue(BUYER_ID, 10L, OrderIssueType.WRONG_ITEM, "  다른 색이 왔어요  ");

            assertThat(response.reviewReason()).isEqualTo(OrderReviewReason.BUYER_REPORTED);
            assertThat(response.issueType()).isEqualTo(OrderIssueType.WRONG_ITEM);
            assertThat(response.issueDetail()).isEqualTo("다른 색이 왔어요");
            verify(eventPublisher).publishEvent(any(OrderReviewRequestedEvent.class));
            assertThatThrownBy(() -> orderService.reportIssue(BUYER_ID, 10L, OrderIssueType.OTHER, null))
                    .extracting("errorCode").isEqualTo(OrderErrorCode.ISSUE_ALREADY_REPORTED);
        }

        @Test
        @DisplayName("판매자는 신고할 수 없고(ORDER_026), 발송 전 주문은 신고할 수 없다(ORDER_023)")
        void reportRejected() {
            Order order = shipped(10L);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
            assertThatThrownBy(() -> orderService.reportIssue(SELLER_ID, 10L, OrderIssueType.OTHER, null))
                    .extracting("errorCode").isEqualTo(OrderErrorCode.NOT_ORDER_BUYER_REPORT);

            Order escrow = shippableOrder(11L);
            given(orderRepository.findByIdForUpdate(11L)).willReturn(Optional.of(escrow));
            assertThatThrownBy(() -> orderService.reportIssue(BUYER_ID, 11L, OrderIssueType.NOT_RECEIVED, null))
                    .extracting("errorCode").isEqualTo(OrderErrorCode.CANNOT_REPORT_ISSUE);
        }

        @Test
        @DisplayName("관리자가 정산으로 처리하면 구매 확정되고 판매자에게 정산한다")
        void resolveConfirmed() {
            Order order = shipped(10L);
            order.reportIssue(OrderIssueType.NOT_RECEIVED, null, LocalDateTime.now());
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            OrderResponse response = orderService.resolveReviewAsConfirmed(10L);

            assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.reviewResult()).isEqualTo(OrderReviewResult.CONFIRMED);
            verify(pointService).settle(SELLER_ID, 500_000L, 10L);
            verify(eventPublisher).publishEvent(any(OrderReviewResolvedEvent.class));
        }

        @Test
        @DisplayName("관리자가 환불로 처리하면 주문을 취소하고 구매자에게 전액 환불한다. 취소 사유는 비워 미발송 제재에 넣지 않는다")
        void resolveRefunded() {
            Order order = shipped(10L);
            order.reportIssue(OrderIssueType.NOT_RECEIVED, null, LocalDateTime.now());
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            OrderResponse response = orderService.resolveReviewAsRefunded(10L);

            assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
            assertThat(response.reviewResult()).isEqualTo(OrderReviewResult.REFUNDED);
            assertThat(response.cancelReason()).isNull();
            verify(pointService).refundOrder(eq(BUYER_ID), eq(500_000L), eq(10L), any());
            verify(pointService, never()).settle(anyLong(), anyLong(), anyLong());
            verify(eventPublisher, never()).publishEvent(any(OrderCanceledEvent.class));
        }

        @Test
        @DisplayName("확인 대기가 아닌 주문은 관리자 처리할 수 없다 (ORDER_025)")
        void notUnderReview() {
            Order order = shipped(10L);
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.resolveReviewAsRefunded(10L))
                    .extracting("errorCode").isEqualTo(OrderErrorCode.NOT_UNDER_REVIEW);
            verify(pointService, never()).refundOrder(anyLong(), anyLong(), anyLong(), any());
        }

        private Order shipped(Long orderId) {
            Order order = shippableOrder(orderId);
            order.ship(Courier.CJ, "1234567890");
            return order;
        }
    }

    @Nested
    @DisplayName("발송 지연 신청")
    class Delay {

        @Test
        @DisplayName("판매자가 신청하면 대기 상태가 되고 메신저 안내 이벤트가 나간다")
        void requestDelay_bySeller_setsPendingAndPublishesEvent() {
            Order order = shippableOrder(1L);
            given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

            OrderResponse response = orderService.requestDelay(SELLER_ID, 1L, "입고 지연");

            assertThat(response.delayStatus()).isEqualTo(OrderDelayStatus.PENDING);
            verify(eventPublisher).publishEvent(new OrderDelayRequestedEvent(1L, 100L, BUYER_ID, SELLER_ID, "아이패드", "입고 지연"));
        }

        @Test
        @DisplayName("구매자는 지연을 신청할 수 없다")
        void requestDelay_byBuyer_throwsNotOrderSellerDelay() {
            given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(shippableOrder(1L)));

            assertThatThrownBy(() -> orderService.requestDelay(BUYER_ID, 1L, "입고 지연"))
                    .extracting("errorCode").isEqualTo(OrderErrorCode.NOT_ORDER_SELLER_DELAY);
        }

        @Test
        @DisplayName("사유에 외부 거래 유도 문구가 있으면 거부하고 주문은 그대로다")
        void requestDelay_contactInReason_throwsDelayReasonNotAllowed() {
            Order order = shippableOrder(1L);
            given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
            given(contentFilter.findViolation("010-1234-5678로 연락주세요"))
                    .willReturn(Optional.of(ContentViolation.values()[0]));

            assertThatThrownBy(() -> orderService.requestDelay(SELLER_ID, 1L, "010-1234-5678로 연락주세요"))
                    .extracting("errorCode").isEqualTo(OrderErrorCode.DELAY_REASON_NOT_ALLOWED);
            assertThat(order.getDelayStatus()).isNull();
        }

        @Test
        @DisplayName("판매자는 지연 신청에 응답할 수 없다")
        void approveDelay_bySeller_throwsNotOrderBuyerDelay() {
            given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingOrder(1L)));

            assertThatThrownBy(() -> orderService.approveDelay(SELLER_ID, 1L))
                    .extracting("errorCode").isEqualTo(OrderErrorCode.NOT_ORDER_BUYER_DELAY);
        }

        @Test
        @DisplayName("기한이 지난 승인은 거부해 자동 취소를 뒤집지 못한다")
        void approveDelay_afterDeadline_throwsDeadlinePassed() {
            Order order = pendingOrder(1L);
            ReflectionTestUtils.setField(order, "shipDeadlineAt", LocalDateTime.now().minusMinutes(1));
            given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.approveDelay(BUYER_ID, 1L))
                    .extracting("errorCode").isEqualTo(OrderErrorCode.DEADLINE_PASSED);
        }

        @Test
        @DisplayName("승인하면 기한이 늘고 결과 이벤트가 나간다")
        void approveDelay_extendsAndPublishesEvent() {
            Order order = pendingOrder(1L);
            LocalDateTime before = order.getShipDeadlineAt();
            given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

            orderService.approveDelay(BUYER_ID, 1L);

            assertThat(order.getShipDeadlineAt()).isEqualTo(before.plusDays(7));
            verify(eventPublisher).publishEvent(new OrderDelayRespondedEvent(1L, 100L, BUYER_ID, SELLER_ID, "아이패드", true));
        }

        @Test
        @DisplayName("거절하면 결과 이벤트가 나간다")
        void rejectDelay_publishesEventWithApprovedFalse() {
            given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingOrder(1L)));

            orderService.rejectDelay(BUYER_ID, 1L);

            verify(eventPublisher).publishEvent(new OrderDelayRespondedEvent(1L, 100L, BUYER_ID, SELLER_ID, "아이패드", false));
        }

        private Order pendingOrder(Long orderId) {
            Order order = shippableOrder(orderId);
            order.requestDelay("입고 지연", LocalDateTime.now());
            return order;
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