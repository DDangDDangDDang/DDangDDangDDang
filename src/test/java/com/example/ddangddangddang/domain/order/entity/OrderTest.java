package com.example.ddangddangddang.domain.order.entity;

import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Nested
    @DisplayName("배송지 입력")
    class DeliveryAddress {

        @Test
        @DisplayName("발송 전에는 배송지를 입력하고 다시 고칠 수 있다")
        void registerAndUpdate() {
            Order order = order();

            order.registerDeliveryAddress("홍길동", "01012345678", "서울시 강남구 테헤란로 1", "문 앞에 놓아주세요");
            order.registerDeliveryAddress("홍길동", "01012345678", "서울시 강남구 테헤란로 2", null);

            assertThat(order.hasDeliveryAddress()).isTrue();
            assertThat(order.getAddress()).isEqualTo("서울시 강남구 테헤란로 2");
            assertThat(order.getDeliveryMemo()).isNull();
        }

        @Test
        @DisplayName("발송한 뒤에는 배송지를 바꿀 수 없다")
        void afterShip() {
            Order order = shippableOrder();
            order.ship(Courier.CJ, "1234567890");

            assertThatThrownBy(() -> order.registerDeliveryAddress("홍길동", "01012345678", "부산시", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.CANNOT_REGISTER_ADDRESS);
        }
    }

    @Nested
    @DisplayName("발송")
    class Ship {

        @Test
        @DisplayName("발송하면 SHIPPING이 되고 택배사, 송장번호, 발송 시각이 기록된다")
        void ship() {
            Order order = shippableOrder();

            order.ship(Courier.CJ, "1234567890");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
            assertThat(order.getCourier()).isEqualTo(Courier.CJ);
            assertThat(order.getTrackingNo()).isEqualTo("1234567890");
            assertThat(order.getShippedAt()).isNotNull();
        }

        @Test
        @DisplayName("배송지가 없으면 발송할 수 없다")
        void withoutAddress() {
            Order order = order();

            assertThatThrownBy(() -> order.ship(Courier.CJ, "1234567890"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ADDRESS_REQUIRED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.ESCROW);
        }

        @Test
        @DisplayName("이미 발송한 주문은 다시 발송할 수 없다")
        void shipTwice() {
            Order order = shippableOrder();
            order.ship(Courier.CJ, "1234567890");

            assertThatThrownBy(() -> order.ship(Courier.CJ, "0000000000"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.CANNOT_SHIP);
        }
    }

    @Nested
    @DisplayName("구매 확정")
    class Confirm {

        @Test
        @DisplayName("발송 후 구매 확정하면 COMPLETED가 된다")
        void confirm() {
            Order order = shippableOrder();
            order.ship(Courier.CJ, "1234567890");

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(order.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("발송 전에는 구매 확정할 수 없다")
        void confirmBeforeShip() {
            Order order = order();

            assertThatThrownBy(order::confirm)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.CANNOT_CONFIRM);
        }

        @Test
        @DisplayName("이미 구매 확정한 주문은 다시 확정할 수 없다")
        void confirmTwice() {
            Order order = shippableOrder();
            order.ship(Courier.CJ, "1234567890");
            order.confirm();

            assertThatThrownBy(order::confirm)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.CANNOT_CONFIRM);
        }
    }

    private Order order() {
        return Order.create(100L, 1L, 2L, "아이패드", "https://img/1.png", 500_000L);
    }

    private Order shippableOrder() {
        Order order = order();
        order.registerDeliveryAddress("홍길동", "01012345678", "서울시 강남구 테헤란로 1", null);
        return order;
    }
    @Nested
    @DisplayName("배송 기한")
    class Deadlines {

        private final LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);

        @Test
        @DisplayName("주문이 생기면 배송지 입력 기한이 24시간 뒤로 잡힌다")
        void create_setsAddressDeadline24HoursLater() {
            LocalDateTime before = LocalDateTime.now();

            Order order = order();

            assertThat(order.getAddressDeadlineAt()).isBetween(before.plusHours(24), LocalDateTime.now().plusHours(24));
        }

        @Test
        @DisplayName("배송지를 처음 입력하면 발송 기한이 7일 뒤로 잡힌다")
        void registerAddress_firstTime_setsShipDeadline() {
            Order order = order();

            address(order, now);

            assertThat(order.getShipDeadlineAt()).isEqualTo(now.plusDays(7));
        }

        @Test
        @DisplayName("배송지를 고쳐도 발송 기한은 늘어나지 않는다")
        void registerAddress_again_keepsShipDeadline() {
            Order order = order();
            address(order, now);

            address(order, now.plusDays(3));

            assertThat(order.getShipDeadlineAt()).isEqualTo(now.plusDays(7));
        }

        @Test
        @DisplayName("지연 승인 뒤 배송지를 고쳐도 연장된 기한은 그대로다")
        void registerAddress_afterDelayApproved_keepsExtendedDeadline() {
            Order order = order();
            address(order, now);
            order.requestDelay("입고 지연", now.plusDays(1));
            order.approveDelay(now.plusDays(2));

            address(order, now.plusDays(3));

            assertThat(order.getShipDeadlineAt()).isEqualTo(now.plusDays(14));
        }

        @Test
        @DisplayName("배송지 입력 기한이 지나면 입력할 수 없다")
        void registerAddress_afterDeadline_throwsDeadlinePassed() {
            Order order = order();

            assertErrorCode(() -> address(order, order.getAddressDeadlineAt()), OrderErrorCode.DEADLINE_PASSED);
        }

        @Test
        @DisplayName("발송 기한이 지나면 배송지도 고칠 수 없다")
        void registerAddress_afterShipDeadline_throwsDeadlinePassed() {
            Order order = order();
            address(order, now);

            assertErrorCode(() -> address(order, now.plusDays(7)), OrderErrorCode.DEADLINE_PASSED);
        }

        @Test
        @DisplayName("발송 기한이 지나면 발송할 수 없다")
        void ship_afterDeadline_throwsDeadlinePassed() {
            Order order = order();
            address(order, now);

            assertErrorCode(() -> order.ship(Courier.CJ, "1234567890", now.plusDays(7)), OrderErrorCode.DEADLINE_PASSED);
        }

        @Test
        @DisplayName("기한이 없는 주문은 기한을 넘긴 것으로 보지 않는다")
        void nullDeadlines_notOverdue() {
            Order order = order();
            org.springframework.test.util.ReflectionTestUtils.setField(order, "addressDeadlineAt", null);

            assertThat(order.isAddressOverdue(now.plusYears(1))).isFalse();
            assertThat(order.isShipOverdue(now.plusYears(1))).isFalse();
        }

        @Test
        @DisplayName("취소하면 사유와 취소 시각이 남는다")
        void cancel_setsReasonAndCanceledAt() {
            Order order = order();

            order.cancel(OrderCancelReason.BUYER_NO_ADDRESS, now);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.BUYER_NO_ADDRESS);
            assertThat(order.getCanceledAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("발송한 주문은 취소할 수 없다")
        void cancel_whenShipping_throwsIllegalState() {
            Order order = shippableOrder();
            order.ship(Courier.CJ, "1234567890");

            assertThatThrownBy(() -> order.cancel(OrderCancelReason.SELLER_NOT_SHIPPED, now))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("발송 지연 신청")
    class Delay {

        private final LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);

        @Test
        @DisplayName("배송지가 없으면 신청할 수 없다")
        void requestDelay_withoutAddress_throwsCannotRequestDelay() {
            assertErrorCode(() -> order().requestDelay("사유", now), OrderErrorCode.CANNOT_REQUEST_DELAY);
        }

        @Test
        @DisplayName("주문당 한 번만 신청할 수 있다")
        void requestDelay_twice_throwsCannotRequestDelay() {
            Order order = pending();

            assertErrorCode(() -> order.requestDelay("다시", now.plusDays(2)), OrderErrorCode.CANNOT_REQUEST_DELAY);
        }

        @Test
        @DisplayName("거절된 뒤에도 다시 신청할 수 없다")
        void requestDelay_afterRejected_throwsCannotRequestDelay() {
            Order order = pending();
            order.rejectDelay(now.plusDays(2));

            assertErrorCode(() -> order.requestDelay("다시", now.plusDays(3)), OrderErrorCode.CANNOT_REQUEST_DELAY);
        }

        @Test
        @DisplayName("기한이 지나면 신청할 수 없다")
        void requestDelay_afterDeadline_throwsDeadlinePassed() {
            Order order = order();
            address(order, now);

            assertErrorCode(() -> order.requestDelay("사유", now.plusDays(7)), OrderErrorCode.DEADLINE_PASSED);
        }

        @Test
        @DisplayName("승인하면 현재 기한이 7일 늘어난다")
        void approveDelay_extendsCurrentDeadlineBy7Days() {
            Order order = pending();

            order.approveDelay(now.plusDays(2));

            assertThat(order.getDelayStatus()).isEqualTo(OrderDelayStatus.APPROVED);
            assertThat(order.getShipDeadlineAt()).isEqualTo(now.plusDays(14));
            assertThat(order.getDelayRespondedAt()).isEqualTo(now.plusDays(2));
        }

        @Test
        @DisplayName("거절하면 기한은 그대로다")
        void rejectDelay_keepsDeadline() {
            Order order = pending();

            order.rejectDelay(now.plusDays(2));

            assertThat(order.getDelayStatus()).isEqualTo(OrderDelayStatus.REJECTED);
            assertThat(order.getShipDeadlineAt()).isEqualTo(now.plusDays(7));
        }

        @Test
        @DisplayName("기한이 지나면 승인할 수 없다")
        void approveDelay_afterDeadline_throwsDeadlinePassed() {
            Order order = pending();

            assertErrorCode(() -> order.approveDelay(now.plusDays(7)), OrderErrorCode.DEADLINE_PASSED);
        }

        @Test
        @DisplayName("대기 중인 신청이 없으면 응답할 수 없다")
        void respond_withoutPending_throwsCannotRespondDelay() {
            Order order = pending();
            order.approveDelay(now.plusDays(1));

            assertErrorCode(() -> order.rejectDelay(now.plusDays(2)), OrderErrorCode.CANNOT_RESPOND_DELAY);
        }

        private Order pending() {
            Order order = order();
            address(order, now);
            order.requestDelay("입고 지연", now.plusDays(1));
            return order;
        }
    }

    private static void address(Order order, LocalDateTime at) {
        order.registerDeliveryAddress("홍길동", "01012345678", "서울시", null, at);
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, OrderErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }
}