package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamStartedEvent;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import com.example.ddangddangddang.domain.seller.repository.SellerSubscriptionRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 판매자가 방송을 시작하면 그 판매자의 구독자에게 알린다. (알림함 + 실시간 + 브라우저 알림, NotificationDispatcher)
 *
 * 시작이 커밋된 뒤 따로 된 스레드(@Async)에서 처리해, 구독자가 많아도 방송 시작 응답이 늦어지지 않는다.
 * 알림이 실패해도 방송은 이미 시작됐으므로 기록만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveStartedNotifier {

    public static final String NOTIFICATION_QUEUE = NotificationDispatcher.NOTIFICATION_QUEUE;

    private final SellerSubscriptionRepository subscriptionRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final UserRepository userRepository;
    private final NotificationDispatcher notificationDispatcher;

    @Async
    @TransactionalEventListener
    public void handleLiveStarted(LiveStreamStartedEvent event) {
        try {
            notifySubscribers(event.liveStreamId());
        } catch (RuntimeException e) {
            log.warn("방송 시작 알림을 보내지 못했습니다. liveStreamId={}", event.liveStreamId(), e);
        }
    }

    void notifySubscribers(Long liveStreamId) {
        LiveStream liveStream = liveStreamRepository.findById(liveStreamId).orElse(null);
        if (liveStream == null) {
            return;
        }
        Long sellerId = liveStream.getSellerId();
        List<Long> subscriberIds = subscriptionRepository.findSubscriberIds(sellerId);
        if (subscriberIds.isEmpty()) {
            return;
        }
        String nickname = userRepository.findById(sellerId).map(User::getNickname).orElse(null);
        notificationDispatcher.dispatch(subscriberIds.stream()
                .map(id -> Notification.liveStarted(id, sellerId, nickname, liveStreamId, liveStream.getTitle()))
                .toList());
    }
}
