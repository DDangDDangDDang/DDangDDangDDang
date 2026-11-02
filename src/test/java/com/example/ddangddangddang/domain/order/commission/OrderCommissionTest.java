package com.example.ddangddangddang.domain.order.commission;

import com.example.ddangddangddang.domain.order.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCommissionTest {

    @Test
    @DisplayName("주문에 수수료율을 저장하고, 판매자는 판매가에서 수수료를 뺀 금액을 받는다 (5%: 10,000P → 9,500P)")
    void settlement() {
        Order order = Order.create(100L, 1L, 2L, "아이패드", null, 10_000L, 500);

        assertThat(order.getCommissionRateBps()).isEqualTo(500);
        assertThat(order.getCommission()).isEqualTo(500L);
        assertThat(order.getSettlementAmount()).isEqualTo(9_500L);
    }

    @Test
    @DisplayName("수수료 도입 전 주문(수수료율 0)은 판매가 그대로 정산한다")
    void noCommission() {
        Order order = Order.create(100L, 1L, 2L, "아이패드", null, 10_000L);

        assertThat(order.getCommission()).isZero();
        assertThat(order.getSettlementAmount()).isEqualTo(10_000L);
    }
}
