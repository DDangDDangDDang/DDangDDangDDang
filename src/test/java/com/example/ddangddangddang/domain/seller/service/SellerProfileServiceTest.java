package com.example.ddangddangddang.domain.seller.service;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.order.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SellerProfileServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Test
    @DisplayName("방송한 시간은 끝난 방송은 시작~종료, 방송 중이면 시작~지금을 더한다")
    void totalBroadcastSeconds() {
        LiveStream ended = stream(NOW.minusHours(3), NOW.minusHours(2));     // 1시간
        LiveStream live = stream(NOW.minusMinutes(30), null);              // 30분

        assertThat(SellerProfileService.totalBroadcastSeconds(List.of(ended, live), NOW)).isEqualTo(3600 + 1800);
        assertThat(SellerProfileService.totalBroadcastSeconds(List.of(), NOW)).isZero();
    }

    @Test
    @DisplayName("평균 발송 시간은 주문부터 발송까지를 시간 단위(소수 첫째 자리)로, 발송한 주문이 없으면 null")
    void averageShippingHours() {
        Order fast = order(NOW.minusHours(10), NOW.minusHours(9));         // 1시간
        Order slow = order(NOW.minusHours(30), NOW.minusHours(28).minusMinutes(30)); // 1.5시간

        assertThat(SellerProfileService.averageShippingHours(List.of(fast, slow))).isEqualTo(1.3);
        assertThat(SellerProfileService.averageShippingHours(List.of())).isNull();
    }

    private LiveStream stream(LocalDateTime startedAt, LocalDateTime endedAt) {
        LiveStream stream = LiveStream.create(1L, "방송");
        ReflectionTestUtils.setField(stream, "startedAt", startedAt);
        ReflectionTestUtils.setField(stream, "endedAt", endedAt);
        return stream;
    }

    private Order order(LocalDateTime createdAt, LocalDateTime shippedAt) {
        Order order = Order.create(1L, 2L, 3L, "상품", null, 10000L);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        ReflectionTestUtils.setField(order, "shippedAt", shippedAt);
        return order;
    }
}
