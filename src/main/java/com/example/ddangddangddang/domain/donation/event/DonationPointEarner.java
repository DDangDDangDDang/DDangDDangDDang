package com.example.ddangddangddang.domain.donation.event;

import com.example.ddangddangddang.domain.donation.entity.DonationLotSource;
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
 * 구매 확정한 주문의 구매자(구매 적립)와 판매자(판매 적립)에게 도네이션 마일리지를 적립하고 알린다.
 * 구매자·판매자는 각자 지갑을 잠그고 따로 된 트랜잭션에서 적립한다. 한쪽이 실패해도 다른 쪽 적립은 남는다.
 *
 * 1. 구매 확정이 커밋되면 따로 된 스레드(@Async)·트랜잭션에서 적립한다.
 *    적립이 실패해도 구매 확정(정산)은 이미 끝났으므로 기록만 남긴다.
 * 2. 실패한 적립은 매일 새벽 보정한다. (backfill) 최근 BACKFILL_WINDOW 동안 구매 확정했는데 구매·판매 적립 묶음이 없는 주문을 찾아 다시 적립한다.
 *    같은 주문으로는 구매자·판매자에게 한 번씩만 적립하고(적립 묶음의 (order_id, source) 유니크), 한도에 걸린 주문도 0P 묶음으로 남으므로 두 번 쌓이거나 다음 달에 적립되지 않는다.
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
        earn(DonationLotSource.PURCHASE, event.orderId(), event.buyerId(), event.sellerId(), event.finalPrice());
        earn(DonationLotSource.SALE, event.orderId(), event.buyerId(), event.sellerId(), event.finalPrice());
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
        try {
            for (DonationLotSource source : List.of(DonationLotSource.PURCHASE, DonationLotSource.SALE)) {
                processed += backfill(source, from, to);
            }
        } catch (RuntimeException e) {
            log.warn("도네이션 포인트 적립 보정을 끝내지 못했습니다. 다음 날 다시 합니다.", e);
        }
        if (processed > 0) {
            log.info("적립하지 못했던 구매 확정 주문 {}건을 다시 처리했습니다.", processed);
        }
        return processed;
    }

    /** 이 source(구매·판매)의 적립 묶음이 없는 주문을 다시 적립한다. @return 다시 처리한 주문 수 */
    private int backfill(DonationLotSource source, LocalDateTime from, LocalDateTime to) {
        int processed = 0;
        // 주문 ID 순으로 넘긴다. 실패한 주문은 이번에는 건너뛰고 다음 날 다시 한다.
        Long afterId = 0L;
        while (true) {
            List<Order> orders = lotRepository.findConfirmedWithoutLot(source, from, to, afterId, PageRequest.of(0, BACKFILL_BATCH_SIZE));
            for (Order order : orders) {
                if (earn(source, order.getId(), order.getBuyerId(), order.getSellerId(), order.getFinalPrice())) {
                    processed++;
                }
            }
            if (orders.size() < BACKFILL_BATCH_SIZE) {
                return processed;
            }
            afterId = orders.get(orders.size() - 1).getId();
        }
    }

    /** @return 처리했으면 true (적립했거나 적립할 마일리지가 없음), 실패했으면 false */
    private boolean earn(DonationLotSource source, Long orderId, Long buyerId, Long sellerId, long finalPrice) {
        Long receiverId = source == DonationLotSource.SALE ? sellerId : buyerId;
        long points;
        try {
            points = source == DonationLotSource.SALE
                    ? donationPointService.earnSale(orderId, sellerId, finalPrice)
                    : donationPointService.earn(orderId, buyerId, sellerId, finalPrice);
        } catch (RuntimeException e) {
            log.warn("도네이션 포인트를 적립하지 못했습니다. 새벽 보정에서 다시 합니다. orderId={}, source={}", orderId, source, e);
            return false;
        }
        // 적립은 끝났으므로 알림이 실패해도 적립 실패로 보지 않는다.
        if (points > 0) {
            try {
                notificationDispatcher.dispatch(List.of(
                        Notification.donationPoint(receiverId, NotificationType.DONATION_POINT_EARNED, points)));
            } catch (RuntimeException e) {
                log.warn("도네이션 포인트 적립 알림을 보내지 못했습니다. orderId={}", orderId, e);
            }
        }
        return true;
    }
}
