package com.example.ddangddangddang.domain.order.entity;

import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
}