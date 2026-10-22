package com.example.ddangddangddang.domain.notification.controller;

import com.example.ddangddangddang.domain.notification.dto.NotificationResponse;
import com.example.ddangddangddang.domain.notification.dto.UnreadNotificationCountResponse;
import com.example.ddangddangddang.domain.notification.service.NotificationService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 알림함. 새 알림은 실시간으로도 온다. (/user/queue/notifications, docs/api/README.md) */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    /** 내 알림 최신순. 다음 페이지는 nextCursor를 cursor로 넘긴다. */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorResponse<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getNotifications(authUser.id(), cursor, size)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> countUnread(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.countUnread(authUser.id())));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> read(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.read(authUser.id(), notificationId)));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> readAll(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.readAll(authUser.id())));
    }
}
