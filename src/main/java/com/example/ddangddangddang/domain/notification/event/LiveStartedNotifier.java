package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamStartedEvent;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.notification.dto.NotificationResponse;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.repository.NotificationRepository;
import com.example.ddangddangddang.domain.seller.repository.SellerSubscriptionRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 판매자가 방송을 시작하면 그 판매자의 구독자에게 알린다.
 * 알림함에 저장하고(접속하지 않았던 구독자도 나중에 본다), 접속 중인 구독자에게는 /user/queue/notifications로 바로 보낸다.
 *
 * 시작이 커밋된 뒤 따로 된 스레드(@Async)에서 처리해, 구독자가 많아도 방송 시작 응답이 늦어지지 않는다.
 * 알림이 실패해도 방송은 이미 시작됐으므로 기록만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveStartedNotifier {

    public static final String NOTIFICATION_QUEUE = "/queue/notifications";

    private final SellerSubscriptionRepository subscriptionRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final StompBroadcaster stompBroadcaster;
    private final TransactionTemplate transactionTemplate;

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

        List<Notification> saved = transactionTemplate.execute(status -> notificationRepository.saveAll(
                subscriberIds.stream()
                        .map(id -> Notification.liveStarted(id, sellerId, nickname, liveStreamId, liveStream.getTitle()))
                        .toList()));
        if (saved == null) {
            return;
        }
        // 저장이 커밋된 뒤에 보낸다. 받은 화면이 알림함을 다시 불러와도 같은 알림이 보이게.
        for (Notification notification : saved) {
            stompBroadcaster.sendToUser(notification.getUserId(), NOTIFICATION_QUEUE, NotificationResponse.from(notification));
        }
    }
}
