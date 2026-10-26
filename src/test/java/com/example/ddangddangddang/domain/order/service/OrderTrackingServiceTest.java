package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.dto.TrackingResponse;
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.infra.DeliveryTrackingClient;
import com.example.ddangddangddang.domain.order.infra.TrackingCacheRepository;
import com.example.ddangddangddang.domain.order.infra.TrackingEvent;
import com.example.ddangddangddang.domain.order.infra.TrackingInfo;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderTrackingServiceTest {

    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final Long ORDER_ID = 10L;

    @InjectMocks
    private OrderTrackingService orderTrackingService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DeliveryTrackingClient deliveryTrackingClient;

    @Mock
    private TrackingCacheRepository trackingCacheRepository;

    private final TrackingEvent inTransit = new TrackingEvent("2026-10-26T10:00:00+09:00", "IN_TRANSIT", "간선상차", "곤지암Hub", null);
    private final TrackingEvent delivered = new TrackingEvent("2026-10-27T14:00:00+09:00", "DELIVERED", "배달완료", "강남", null);

    @Test
    @DisplayName("캐시가 없으면 조회하고, 배송 중이면 10분 동안 캐시한다")
    void fetchAndCache() {
        givenShippedOrder(Courier.CJ, "123");
        TrackingInfo info = new TrackingInfo(inTransit, List.of(inTransit));
        given(trackingCacheRepository.find(Courier.CJ, "123")).willReturn(Optional.empty());
        given(deliveryTrackingClient.track(Courier.CJ, "123")).willReturn(info);

        TrackingResponse response = orderTrackingService.getTracking(BUYER, ORDER_ID);

        assertThat(response.courier()).isEqualTo(Courier.CJ);
        assertThat(response.trackingNo()).isEqualTo("123");
        assertThat(response.lastEvent()).isEqualTo(inTransit);
        verify(trackingCacheRepository).save(Courier.CJ, "123", info, OrderTrackingService.CACHE_TTL);
    }

    @Test
    @DisplayName("배달 완료면 하루 동안 캐시한다")
    void deliveredCachedLonger() {
        givenShippedOrder(Courier.CJ, "123");
        TrackingInfo info = new TrackingInfo(delivered, List.of(inTransit, delivered));
        given(trackingCacheRepository.find(Courier.CJ, "123")).willReturn(Optional.empty());
        given(deliveryTrackingClient.track(Courier.CJ, "123")).willReturn(info);

        orderTrackingService.getTracking(SELLER, ORDER_ID);

        verify(trackingCacheRepository).save(Courier.CJ, "123", info, OrderTrackingService.DELIVERED_CACHE_TTL);
    }

    @Test
    @DisplayName("캐시가 있으면 외부 API를 부르지 않는다")
    void cacheHit() {
        givenShippedOrder(Courier.CJ, "123");
        given(trackingCacheRepository.find(Courier.CJ, "123")).willReturn(Optional.of(new TrackingInfo(inTransit, List.of(inTransit))));

        TrackingResponse response = orderTrackingService.getTracking(BUYER, ORDER_ID);

        assertThat(response.events()).containsExactly(inTransit);
        verify(deliveryTrackingClient, never()).track(any(), anyString());
    }

    @Test
    @DisplayName("구매자·판매자가 아니면 ORDER_003")
    void stranger() {
        givenShippedOrder(Courier.CJ, "123");

        assertThatThrownBy(() -> orderTrackingService.getTracking(99L, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.NOT_ORDER_PARTICIPANT);
    }

    @Test
    @DisplayName("발송 전(송장 없음)이면 ORDER_012")
    void notShipped() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order()));

        assertThatThrownBy(() -> orderTrackingService.getTracking(BUYER, ORDER_ID))
                .extracting("errorCode").isEqualTo(OrderErrorCode.TRACKING_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("기타 택배사면 조회하지 않고 ORDER_013")
    void etcCourier() {
        givenShippedOrder(Courier.ETC, "123");

        assertThatThrownBy(() -> orderTrackingService.getTracking(BUYER, ORDER_ID))
                .extracting("errorCode").isEqualTo(OrderErrorCode.TRACKING_UNSUPPORTED_COURIER);
        verify(trackingCacheRepository, never()).find(any(), anyString());
    }

    @Test
    @DisplayName("조회 실패는 캐시하지 않는다")
    void failureNotCached() {
        givenShippedOrder(Courier.CJ, "123");
        given(trackingCacheRepository.find(Courier.CJ, "123")).willReturn(Optional.empty());
        given(deliveryTrackingClient.track(Courier.CJ, "123"))
                .willThrow(new BusinessException(OrderErrorCode.TRACKING_NOT_FOUND));

        assertThatThrownBy(() -> orderTrackingService.getTracking(BUYER, ORDER_ID))
                .extracting("errorCode").isEqualTo(OrderErrorCode.TRACKING_NOT_FOUND);
        verify(trackingCacheRepository, never()).save(any(), anyString(), any(), any());
    }

    private void givenShippedOrder(Courier courier, String trackingNo) {
        Order order = order();
        ReflectionTestUtils.setField(order, "courier", courier);
        ReflectionTestUtils.setField(order, "trackingNo", trackingNo);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    }

    private Order order() {
        Order order = Order.create(100L, BUYER, SELLER, "아이패드", null, 500_000L);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }
}
