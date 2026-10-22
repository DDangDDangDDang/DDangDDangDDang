package com.example.ddangddangddang.domain.notification.push;

import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 브라우저 알림(웹 푸시). 탭을 닫아도 브라우저가 켜져 있으면 알림함의 새 알림이 브라우저 알림으로 온다.
 * 순서: 공개키 받기 → 브라우저에서 pushManager.subscribe(applicationServerKey=공개키) → 구독 저장
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    /** VAPID 공개키 (base64url). 서버 키가 바뀌면 브라우저는 구독을 다시 만든다. */
    @GetMapping("/public-key")
    public ResponseEntity<ApiResponse<PushPublicKeyResponse>> publicKey() {
        return ResponseEntity.ok(ApiResponse.ok(new PushPublicKeyResponse(pushSubscriptionService.publicKey())));
    }

    /** 이 브라우저의 구독 저장. 같은 브라우저면 덮어쓴다. */
    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<Void>> subscribe(@AuthenticationPrincipal AuthUser authUser,
                                                       @Valid @RequestBody PushSubscriptionRequest request) {
        pushSubscriptionService.subscribe(authUser.id(), request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** 이 브라우저의 구독 삭제 (알림 끄기·로그아웃) */
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal AuthUser authUser, @RequestParam String endpoint) {
        pushSubscriptionService.unsubscribe(authUser.id(), endpoint);
        return ResponseEntity.noContent().build();
    }
}
