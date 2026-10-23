package com.example.ddangddangddang.domain.notification.service;

import com.example.ddangddangddang.domain.notification.dto.NotificationResponse;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationCategory;
import com.example.ddangddangddang.domain.notification.push.WebPushSender;
import com.example.ddangddangddang.domain.notification.repository.NotificationMuteRepository;
import com.example.ddangddangddang.domain.notification.repository.NotificationRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 알림 보내기의 공통 순서.
 * 1. 알림함에 저장하고 커밋한다. (접속하지 않았던 회원도 나중에 본다)
 * 2. 접속 중인 회원에게 /user/queue/notifications로 바로 보낸다. (받은 화면이 알림함을 다시 불러와도 같은 알림이 보이게 커밋 뒤에)
 * 3. 브라우저 알림을 켠 기기에 웹 푸시로 보낸다. (탭을 닫아도 온다)
 *
 * 2·3이 실패해도 알림함에는 남으므로 기록만 한다. 트랜잭션 밖(커밋 뒤 이벤트, 스케줄러)에서 부른다.
 * 회원이 알림 설정에서 끈 묶음(NotificationMute)의 알림은 저장하지도 보내지도 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    public static final String NOTIFICATION_QUEUE = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final NotificationMuteRepository muteRepository;
    private final StompBroadcaster stompBroadcaster;
    private final WebPushSender webPushSender;
    private final TransactionTemplate transactionTemplate;

    public List<Notification> dispatch(List<Notification> notifications) {
        List<Notification> wanted = withoutMuted(notifications);
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Notification> saved = transactionTemplate.execute(status -> notificationRepository.saveAll(wanted));
        if (saved == null) {
            return List.of();
        }
        deliver(saved);
        return saved;
    }

    /** 받는 회원이 알림 설정에서 끈 묶음의 알림을 뺀다. (다른 기록과 한 트랜잭션에 직접 저장하는 곳도 저장 전에 부른다) */
    public List<Notification> withoutMuted(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return List.of();
        }
        Set<String> muted = muteRepository.findByUserIdIn(notifications.stream().map(Notification::getUserId).distinct().toList())
                .stream()
                .map(mute -> mute.getUserId() + ":" + mute.getCategory())
                .collect(Collectors.toSet());
        if (muted.isEmpty()) {
            return notifications;
        }
        return notifications.stream()
                .filter(n -> !muted.contains(n.getUserId() + ":" + NotificationCategory.of(n.getType())))
                .toList();
    }

    /** 이미 저장(커밋)된 알림을 실시간·웹 푸시로 보낸다. (다른 기록과 한 트랜잭션에 알림을 저장한 경우) */
    public void deliver(List<Notification> saved) {
        for (Notification notification : saved) {
            try {
                stompBroadcaster.sendToUser(notification.getUserId(), NOTIFICATION_QUEUE, NotificationResponse.from(notification));
            } catch (RuntimeException e) {
                log.warn("실시간 알림 전송 실패 notificationId={}", notification.getId(), e);
            }
        }
        try {
            webPushSender.send(saved);
        } catch (RuntimeException e) {
            log.warn("웹 푸시 전송 실패", e);
        }
    }
}
