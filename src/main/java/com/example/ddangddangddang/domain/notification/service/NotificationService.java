package com.example.ddangddangddang.domain.notification.service;

import com.example.ddangddangddang.domain.notification.dto.NotificationResponse;
import com.example.ddangddangddang.domain.notification.dto.UnreadNotificationCountResponse;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.exception.NotificationErrorCode;
import com.example.ddangddangddang.domain.notification.repository.NotificationRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 알림함: 내 알림 목록, 안 읽은 수, 읽음 처리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public CursorResponse<NotificationResponse> getNotifications(Long userId, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return CursorResponse.of(notificationRepository.findByCursor(userId, cursor, PageRequest.of(0, pageSize + 1)),
                pageSize, NotificationResponse::from, Notification::getId);
    }

    public UnreadNotificationCountResponse countUnread(Long userId) {
        return new UnreadNotificationCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    /** 알림 하나를 읽음으로. 내 알림이 아니면 찾을 수 없다고 한다. */
    @Transactional
    public NotificationResponse read(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOT_FOUND));
        notification.read(LocalDateTime.now());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public UnreadNotificationCountResponse readAll(Long userId) {
        notificationRepository.readAll(userId, LocalDateTime.now());
        return new UnreadNotificationCountResponse(0);
    }
}
