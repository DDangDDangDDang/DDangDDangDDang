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

import java.time.Duration;
import java.time.LocalDateTime;
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

    // 판매자가 방송을 껐다 켜기를 반복해도 구독자에게는 이 간격에 한 번만 알린다.
    static final Duration RENOTIFY_INTERVAL = Duration.ofMinutes(30);

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
        if (notifiedRecently(sellerId)) {
            log.info("최근 {} 안에 방송 시작을 알려 이번에는 알리지 않습니다. sellerId={}, liveStreamId={}", RENOTIFY_INTERVAL, sellerId, liveStreamId);
            return;
        }
        List<Long> subscriberIds = subscriptionRepository.findSubscriberIds(sellerId);
        if (subscriberIds.isEmpty()) {
            return;
        }
        // 보내기 전에 남긴다. 보내다 실패해도 같은 판매자의 다음 재시작에서 겹쳐 보내지 않게 한다.
        liveStreamRepository.markSubscribersNotified(liveStreamId, LocalDateTime.now());
        String nickname = userRepository.findById(sellerId).map(User::getNickname).orElse(null);
        notificationDispatcher.dispatch(subscriberIds.stream()
                .map(id -> Notification.liveStarted(id, sellerId, nickname, liveStreamId, liveStream.getTitle(), liveStream.isRecorded()))
                .toList());
    }

    /**
     * 이 판매자의 방송으로 RENOTIFY_INTERVAL 안에 구독자에게 알린 적이 있는지.
     * 시작한 시각이 아니라 실제로 알린 시각을 본다. 그래야 30분 안에 껐다 켜기를 이어 가도, 마지막 알림에서 30분이 지나면 다시 알린다.
     */
    private boolean notifiedRecently(Long sellerId) {
        return liveStreamRepository.existsBySellerIdAndSubscribersNotifiedAtAfter(
                sellerId, LocalDateTime.now().minus(RENOTIFY_INTERVAL));
    }
}
