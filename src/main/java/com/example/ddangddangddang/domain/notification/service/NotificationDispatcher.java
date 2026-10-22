package com.example.ddangddangddang.domain.notification.service;

import com.example.ddangddangddang.domain.notification.dto.NotificationResponse;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.push.WebPushSender;
import com.example.ddangddangddang.domain.notification.repository.NotificationRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 알림 보내기의 공통 순서.
 * 1. 알림함에 저장하고 커밋한다. (접속하지 않았던 회원도 나중에 본다)
 * 2. 접속 중인 회원에게 /user/queue/notifications로 바로 보낸다. (받은 화면이 알림함을 다시 불러와도 같은 알림이 보이게 커밋 뒤에)
 * 3. 브라우저 알림을 켠 기기에 웹 푸시로 보낸다. (탭을 닫아도 온다)
 *
 * 2·3이 실패해도 알림함에는 남으므로 기록만 한다. 트랜잭션 밖(커밋 뒤 이벤트, 스케줄러)에서 부른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    public static final String NOTIFICATION_QUEUE = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final StompBroadcaster stompBroadcaster;
    private final WebPushSender webPushSender;
    private final TransactionTemplate transactionTemplate;

    public List<Notification> dispatch(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return List.of();
        }
        List<Notification> saved = transactionTemplate.execute(status -> notificationRepository.saveAll(notifications));
        if (saved == null) {
            return List.of();
        }
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
        return saved;
    }
}
