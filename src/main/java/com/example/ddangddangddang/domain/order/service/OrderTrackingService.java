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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 주문 배송 조회. 주문의 구매자·판매자만 볼 수 있다.
 *
 * 외부 API(배송 조회)를 부르는 동안 DB 트랜잭션을 잡고 있지 않도록 트랜잭션 없이 실행한다.
 * 같은 송장은 잠깐 캐시한다. 배달 완료는 더 바뀌지 않으므로 오래 둔다.
 */
@Slf4j
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

        return TrackingResponse.of(courier, trackingNo, track(courier, trackingNo));
    }

    /**
     * 자동 구매 확정 전에 송장을 확인한다. (OrderAutoConfirmScheduler) 예외를 던지지 않고 결과로 알려 준다.
     * - 배송 완료면 DELIVERED, 조회는 되지만 아직이면 IN_TRANSIT
     * - 택배사에 송장 정보가 없으면 NOT_FOUND (발송 후 7일이 지났는데도 없으면 잘못되거나 가짜인 송장일 수 있다)
     * - 조회 서비스 장애·미설정이면 UNAVAILABLE, 조회할 수 없는 택배사(기타)면 UNSUPPORTED
     */
    public TrackingCheck checkForAutoConfirm(Courier courier, String trackingNo) {
        if (courier == null || trackingNo == null || !DeliveryTrackingClient.supports(courier)) {
            return TrackingCheck.UNSUPPORTED;
        }
        try {
            return track(courier, trackingNo).isDelivered() ? TrackingCheck.DELIVERED : TrackingCheck.IN_TRANSIT;
        } catch (BusinessException e) {
            return e.getErrorCode() == OrderErrorCode.TRACKING_NOT_FOUND ? TrackingCheck.NOT_FOUND : TrackingCheck.UNAVAILABLE;
        } catch (RuntimeException e) {
            log.warn("자동 구매 확정 전 배송 조회 실패 courier={}", courier, e);
            return TrackingCheck.UNAVAILABLE;
        }
    }

    // 같은 송장은 잠깐 캐시한다. 배달 완료는 더 바뀌지 않으므로 오래 둔다.
    private TrackingInfo track(Courier courier, String trackingNo) {
        return trackingCacheRepository.find(courier, trackingNo).orElseGet(() -> {
            TrackingInfo fetched = deliveryTrackingClient.track(courier, trackingNo);
            trackingCacheRepository.save(courier, trackingNo, fetched,
                    fetched.isDelivered() ? DELIVERED_CACHE_TTL : CACHE_TTL);
            return fetched;
        });
    }
}