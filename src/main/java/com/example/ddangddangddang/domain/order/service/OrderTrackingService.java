package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.dto.TrackingResponse;
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.infra.DeliveryTrackingClient;
import com.example.ddangddangddang.domain.order.infra.TrackingCacheRepository;
import com.example.ddangddangddang.domain.order.infra.TrackingInfo;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 주문 배송 조회. 주문의 구매자·판매자만 볼 수 있다.
 *
 * 외부 API(배송 조회)를 부르는 동안 DB 트랜잭션을 잡고 있지 않도록 트랜잭션 없이 실행한다.
 * 같은 송장은 잠깐 캐시한다. 배달 완료는 더 바뀌지 않으므로 오래 둔다.
 */
@Service
@RequiredArgsConstructor
public class OrderTrackingService {

    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    static final Duration DELIVERED_CACHE_TTL = Duration.ofDays(1);

    private final OrderRepository orderRepository;
    private final DeliveryTrackingClient deliveryTrackingClient;
    private final TrackingCacheRepository trackingCacheRepository;

    public TrackingResponse getTracking(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.isParticipant(userId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_PARTICIPANT);
        }

        Courier courier = order.getCourier();
        String trackingNo = order.getTrackingNo();
        if (courier == null || trackingNo == null) {
            throw new BusinessException(OrderErrorCode.TRACKING_NOT_AVAILABLE);
        }
        if (!DeliveryTrackingClient.supports(courier)) {
            throw new BusinessException(OrderErrorCode.TRACKING_UNSUPPORTED_COURIER);
        }

        TrackingInfo info = trackingCacheRepository.find(courier, trackingNo).orElseGet(() -> {
            TrackingInfo fetched = deliveryTrackingClient.track(courier, trackingNo);
            trackingCacheRepository.save(courier, trackingNo, fetched,
                    fetched.isDelivered() ? DELIVERED_CACHE_TTL : CACHE_TTL);
            return fetched;
        });
        return TrackingResponse.of(courier, trackingNo, info);
    }
}