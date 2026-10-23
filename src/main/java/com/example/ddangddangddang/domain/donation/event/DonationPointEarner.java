package com.example.ddangddangddang.domain.donation.event;

import com.example.ddangddangddang.domain.donation.service.DonationPointService;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import com.example.ddangddangddang.domain.order.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 구매 확정이 커밋되면 구매자에게 도네이션 포인트를 적립하고 알린다.
 *
 * 따로 된 스레드(@Async)·트랜잭션에서 한다. 적립이 실패해도 구매 확정(정산)은 이미 끝났으므로 기록만 남긴다.
 * 같은 주문으로는 한 번만 적립하므로(적립 묶음의 order_id 유니크) 나중에 다시 적립해도 두 번 쌓이지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DonationPointEarner {

    private final DonationPointService donationPointService;
    private final NotificationDispatcher notificationDispatcher;

    @Async
    @TransactionalEventListener
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        try {
            long earned = donationPointService.earn(event.orderId(), event.buyerId(), event.sellerId(), event.finalPrice());
            if (earned > 0) {
                notificationDispatcher.dispatch(List.of(
                        Notification.donationPoint(event.buyerId(), NotificationType.DONATION_POINT_EARNED, earned)));
            }
        } catch (RuntimeException e) {
            log.warn("도네이션 포인트를 적립하지 못했습니다. orderId={}", event.orderId(), e);
        }
    }
}
