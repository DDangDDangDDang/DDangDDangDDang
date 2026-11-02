package com.example.ddangddangddang.domain.livechat.controller;

import com.example.ddangddangddang.domain.livechat.service.LiveChatService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자의 방송 채팅 관리. 채팅 전송은 STOMP(LiveChatController)로 하고, 관리 동작은 REST로 받아 로그인·권한을 확인한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/live-streams/{liveStreamId}/chat/messages")
public class LiveChatModerationController {

    private final LiveChatService liveChatService;

    /** AI가 자동으로 가린 메시지를 되돌린다. 시청자 화면에 MESSAGE_RESTORED가 전달된다. */
    @PostMapping("/{messageId}/restore")
    public ResponseEntity<ApiResponse<Void>> restore(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId,
            @PathVariable String messageId) {
        liveChatService.restoreHiddenMessage(liveStreamId, authUser.id(), messageId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
