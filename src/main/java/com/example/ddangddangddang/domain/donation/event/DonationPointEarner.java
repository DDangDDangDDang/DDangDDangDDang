package com.example.ddangddangddang.domain.donation.event;

import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.domain.donation.service.DonationPointService;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.event.OrderConfirmedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 구매 확정한 구매자에게 도네이션 포인트를 적립하고 알린다.
 *
 * 1. 구매 확정이 커밋되면 따로 된 스레드(@Async)·트랜잭션에서 적립한다.
 *    적립이 실패해도 구매 확정(정산)은 이미 끝났으므로 기록만 남긴다.
 * 2. 실패한 적립은 매일 새벽 보정한다. (backfill) 최근 BACKFILL_WINDOW 동안 구매 확정했는데 적립 묶음이 없는 주문을 찾아 다시 적립한다.
 *    같은 주문으로는 한 번만 적립하고(적립 묶음의 order_id 유니크), 한도에 걸린 주문도 0P 묶음으로 남으므로 두 번 쌓이거나 다음 달에 적립되지 않는다.
 *    방금 확정해 이벤트 처리 중인 주문과 겹치지 않도록 BACKFILL_DELAY보다 오래된 주문만 본다.
 */
@Slf4j
@Component
public class DonationPointEarner {

    static final Duration BACKFILL_WINDOW = Duration.ofDays(7);
    static final Duration BACKFILL_DELAY = Duration.ofMinutes(10);
    static final int BACKFILL_BATCH_SIZE = 200;

    private final DonationPointService donationPointService;
    private final DonationPointLotRepository lotRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final boolean backfillEnabled;

    public DonationPointEarner(DonationPointService donationPointService, DonationPointLotRepository lotRepository,
                               NotificationDispatcher notificationDispatcher,
                               @Value("${donation.backfill.enabled:true}") boolean backfillEnabled) {
        this.donationPointService = donationPointService;
        this.lotRepository = lotRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.backfillEnabled = backfillEnabled;
    }

    @Async
    @TransactionalEventListener
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        earn(event.orderId(), event.buyerId(), event.sellerId(), event.finalPrice());
    }

    @Scheduled(cron = "${donation.backfill.cron:0 20 4 * * *}")
    public void scheduled() {
        if (backfillEnabled) {
            backfill();
        }
    }

    /** 적립하지 못한 최근 구매 확정 주문을 다시 적립한다. @return 다시 처리한 주문 수 */
    public int backfill() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minus(BACKFILL_WINDOW);
        LocalDateTime to = now.minus(BACKFILL_DELAY);
        int processed = 0;
        int failed = 0;
        try {
            while (true) {
                // 적립에 성공한(0P 포함) 주문은 다음 조회에서 빠진다. 실패한 주문이 앞에 남아 같은 페이지를 계속 받지 않도록 그만큼 건너뛴다.
                List<Order> orders = lotRepository.findConfirmedWithoutLot(from, to, PageRequest.of(0, BACKFILL_BATCH_SIZE + failed));
                List<Order> todo = orders.subList(Math.min(failed, orders.size()), orders.size());
                if (todo.isEmpty()) {
                    break;
                }
                for (Order order : todo) {
                    if (earn(order.getId(), order.getBuyerId(), order.getSellerId(), order.getFinalPrice())) {
                        processed++;
                    } else {
                        failed++;
                    }
                }
                if (orders.size() < BACKFILL_BATCH_SIZE + failed) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.warn("도네이션 포인트 적립 보정을 끝내지 못했습니다. 다음 날 다시 합니다.", e);
        }
        if (processed > 0) {
            log.info("적립하지 못했던 구매 확정 주문 {}건을 다시 처리했습니다.", processed);
        }
        return processed;
    }

    /** @return 처리했으면 true (적립했거나 적립할 포인트가 없음), 실패했으면 false */
    private boolean earn(Long orderId, Long buyerId, Long sellerId, long finalPrice) {
        try {
            long points = donationPointService.earn(orderId, buyerId, sellerId, finalPrice);
            if (points > 0) {
                notificationDispatcher.dispatch(List.of(
                        Notification.donationPoint(buyerId, NotificationType.DONATION_POINT_EARNED, points)));
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("도네이션 포인트를 적립하지 못했습니다. 새벽 보정에서 다시 합니다. orderId={}", orderId, e);
            return false;
        }
    }
}
