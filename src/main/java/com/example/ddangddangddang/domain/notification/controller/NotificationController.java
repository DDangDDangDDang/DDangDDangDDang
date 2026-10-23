package com.example.ddangddangddang.domain.notification.controller;

import com.example.ddangddangddang.domain.notification.dto.NotificationResponse;
import com.example.ddangddangddang.domain.notification.dto.NotificationSettingRequest;
import com.example.ddangddangddang.domain.notification.dto.NotificationSettingResponse;
import com.example.ddangddangddang.domain.notification.entity.NotificationCategory;
import com.example.ddangddangddang.domain.notification.dto.UnreadNotificationCountResponse;
import com.example.ddangddangddang.domain.notification.service.NotificationService;
import com.example.ddangddangddang.domain.notification.service.NotificationSettingService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 알림함. 새 알림은 실시간으로도 온다. (/user/queue/notifications, docs/api/README.md) */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSettingService notificationSettingService;

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

    /** 알림 종류(묶음)별 켜짐 여부. 기본은 모두 켜짐 */
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> getSettings(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(notificationSettingService.getSettings(authUser.id())));
    }

    /** 한 묶음을 켜거나 끈다. 끈 묶음의 알림은 알림함에도 쌓이지 않고 실시간·브라우저 알림도 오지 않는다. */
    @PutMapping("/settings/{category}")
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> updateSetting(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable NotificationCategory category,
            @Valid @RequestBody NotificationSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(notificationSettingService.update(authUser.id(), category, request.enabled())));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> readAll(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.readAll(authUser.id())));
    }
}
