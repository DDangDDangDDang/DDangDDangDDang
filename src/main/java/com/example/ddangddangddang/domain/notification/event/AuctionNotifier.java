package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.event.AuctionCanceledEvent;
import com.example.ddangddangddang.domain.auction.event.AuctionEndedEvent;
import com.example.ddangddangddang.domain.auction.event.AuctionStartedEvent;
import com.example.ddangddangddang.domain.auction.event.BidOutbidEvent;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.repository.BidRepository;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.repository.AuctionAlertRepository;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 경매가 바뀌면 관계있는 회원에게 알린다. (알림함 + 실시간 + 브라우저 알림, NotificationDispatcher)
 *
 * | 일                | 받는 사람                                             |
 * |-------------------|-------------------------------------------------------|
 * | 시작              | 알림 받기를 한 회원                                    |
 * | 상위 입찰         | 최고 입찰자에서 밀려난 회원                             |
 * | 마감 10분 전      | 알림 받기 + 입찰한 회원 (AuctionClosingSoonNotifier)   |
 * | 취소              | 알림 받기 + 입찰한 회원                                 |
 * | 낙찰(즉시 구매 포함) | 낙찰자: 낙찰, 판매자: 낙찰됨, 그 밖의 알림 받기·입찰자: 종료 |
 * | 유찰              | 판매자: 유찰, 알림 받기를 한 회원: 종료                  |
 *
 * 경매가 커밋된 뒤 따로 된 스레드(@Async)에서 처리해 입찰·마감을 늦추지 않는다. 알림이 실패해도 경매는 그대로이므로 기록만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionNotifier {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionAlertRepository auctionAlertRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final TransactionTemplate transactionTemplate;

    @Async
    @TransactionalEventListener
    public void handleStarted(AuctionStartedEvent event) {
        run(event.auctionId(), "시작", () -> {
            String productName = productName(event.auctionId());
            notificationDispatcher.dispatch(notifications(auctionAlertRepository.findUserIds(event.auctionId()),
                    NotificationType.AUCTION_STARTED, event.auctionId(), productName, null));
        });
    }

    @Async
    @TransactionalEventListener
    public void handleOutbid(BidOutbidEvent event) {
        run(event.auctionId(), "상위 입찰", () -> notificationDispatcher.dispatch(notifications(List.of(event.outbidUserId()),
                NotificationType.AUCTION_OUTBID, event.auctionId(), productName(event.auctionId()), event.newAmount())));
    }

    @Async
    @TransactionalEventListener
    public void handleCanceled(AuctionCanceledEvent event) {
        run(event.auctionId(), "취소", () -> notificationDispatcher.dispatch(notifications(watchers(event.auctionId()),
                NotificationType.AUCTION_CANCELED, event.auctionId(), productName(event.auctionId()), null)));
    }

    @Async
    @TransactionalEventListener
    public void handleEnded(AuctionEndedEvent event) {
        run(event.auctionId(), "종료", () -> notificationDispatcher.dispatch(endedNotifications(event, watchers(event.auctionId()))));
    }

    /** 낙찰자·판매자에게는 결과를, 그 밖의 알림 받기·입찰자에게는 종료를 알린다. */
    static List<Notification> endedNotifications(AuctionEndedEvent event, Collection<Long> watchers) {
        boolean sold = event.result() == AuctionStatus.ENDED && event.winnerId() != null;
        List<Notification> result = new ArrayList<>();
        if (sold) {
            result.add(Notification.auction(event.winnerId(), NotificationType.AUCTION_WON, event.auctionId(), event.productName(), event.finalPrice()));
            result.add(Notification.auction(event.sellerId(), NotificationType.AUCTION_SOLD, event.auctionId(), event.productName(), event.finalPrice()));
        } else {
            result.add(Notification.auction(event.sellerId(), NotificationType.AUCTION_FAILED, event.auctionId(), event.productName(), null));
        }
        List<Long> others = watchers.stream()
                .filter(id -> !id.equals(event.sellerId()) && !Objects.equals(id, sold ? event.winnerId() : null))
                .toList();
        result.addAll(notifications(others, NotificationType.AUCTION_ENDED, event.auctionId(), event.productName(), null));
        return result;
    }

    /** 알림 받기를 한 회원 + 입찰한 회원 (중복 없이) */
    List<Long> watchers(Long auctionId) {
        Set<Long> ids = new LinkedHashSet<>(auctionAlertRepository.findUserIds(auctionId));
        ids.addAll(bidRepository.findBidderIds(auctionId));
        return List.copyOf(ids);
    }

    static List<Notification> notifications(Collection<Long> userIds, NotificationType type, Long auctionId, String productName, Long price) {
        return userIds.stream().map(id -> Notification.auction(id, type, auctionId, productName, price)).toList();
    }

    /** 상품은 지연 로딩이라 트랜잭션 안에서 꺼낸다. (이벤트는 커밋 뒤 다른 스레드에서 처리) */
    private String productName(Long auctionId) {
        return transactionTemplate.execute(status ->
                auctionRepository.findById(auctionId).map(auction -> auction.getProduct().getName()).orElse(null));
    }

    private void run(Long auctionId, String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.warn("경매 {} 알림을 보내지 못했습니다. auctionId={}", what, auctionId, e);
        }
    }
}
