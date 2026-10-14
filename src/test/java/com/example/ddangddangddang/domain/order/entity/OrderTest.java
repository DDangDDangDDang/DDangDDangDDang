package com.example.ddangddangddang.domain.order.entity;

import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    @DisplayName("발송하면 SHIPPING이 되고 송장번호와 발송 시각이 기록된다")
    void ship() {
        Order order = order();

        order.ship("1234567890");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(order.getTrackingNo()).isEqualTo("1234567890");
        assertThat(order.getShippedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 발송한 주문은 다시 발송할 수 없다")
    void shipTwice() {
        Order order = order();
        order.ship("1234567890");

        assertThatThrownBy(() -> order.ship("0000000000"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANNOT_SHIP);
    }

    @Test
    @DisplayName("발송 후 구매 확정하면 COMPLETED가 된다")
    void confirm() {
        Order order = order();
        order.ship("1234567890");

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
        Order order = order();
        order.ship("1234567890");
        order.confirm();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANNOT_CONFIRM);
    }

    private Order order() {
        return Order.create(100L, 1L, 2L, "아이패드", "https://img/1.png", 500_000L);
    }
}