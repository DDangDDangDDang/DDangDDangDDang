package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import com.example.ddangddangddang.domain.order.event.OrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 기한 초과로 주문이 취소되면 구매자와 판매자에게 알린다.
 * 주문이 커밋된 뒤 다른 스레드에서 처리하고, 실패해도 취소는 그대로이므로 기록만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCanceledNotifier {

    private final NotificationDispatcher notificationDispatcher;

    @Async
    @TransactionalEventListener
    public void handle(OrderCanceledEvent event) {
        try {
            Long penalty = event.penaltyAmount() > 0 ? event.penaltyAmount() : null;
            notificationDispatcher.dispatch(List.of(
                    Notification.auction(event.buyerId(), NotificationType.ORDER_CANCELED, event.auctionId(), event.productName(), penalty),
                    Notification.auction(event.sellerId(), NotificationType.ORDER_CANCELED, event.auctionId(), event.productName(), penalty)));
        } catch (RuntimeException e) {
            log.warn("주문 취소 알림을 보내지 못했습니다. orderId={}", event.orderId(), e);
        }
    }
}
