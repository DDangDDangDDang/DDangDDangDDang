package com.example.ddangddangddang.domain.directmessage.controller;

import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmMessageSendRequest;
import com.example.ddangddangddang.domain.directmessage.dto.DmReadRequest;
import com.example.ddangddangddang.domain.directmessage.dto.DmReadResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmReportRequest;
import com.example.ddangddangddang.domain.directmessage.dto.DmReportResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmRoomCreateRequest;
import com.example.ddangddangddang.domain.directmessage.dto.DmRoomResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmUnreadCountResponse;
import com.example.ddangddangddang.domain.directmessage.service.DirectMessageService;
import com.example.ddangddangddang.domain.directmessage.service.DmReportService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 회원 간 1:1 채팅. 보내기·읽음은 이 API로 하고, 받기는 WebSocket /user/queue/dm 구독으로 한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dm")
public class DirectMessageController {

    private final DirectMessageService directMessageService;
    private final DmReportService dmReportService;

    /**
     * 상대와의 대화방을 가져온다. 없으면 만든다. (같은 두 사람 사이의 방은 하나)
     */
    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<DmRoomResponse>> getOrCreateRoom(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody DmRoomCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(directMessageService.getOrCreateRoom(authUser.id(), request.targetUserId())));
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<CursorResponse<DmRoomResponse>>> getRooms(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(directMessageService.getRooms(authUser.id(), cursor, size)));
    }

    /**
     * 내 모든 대화방의 읽지 않은 메시지 수 합계 (상단 알림 배지)
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<DmUnreadCountResponse>> getUnreadCount(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(directMessageService.getUnreadCount(authUser.id())));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<CursorResponse<DmMessageResponse>>> getMessages(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.ok(directMessageService.getMessages(authUser.id(), roomId, cursor, size)));
    }

    @PostMapping("/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<DmMessageResponse>> send(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @Valid @RequestBody DmMessageSendRequest request) {
        DmMessageResponse response = directMessageService.send(authUser.id(), roomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /**
     * 읽음 처리. 본문을 비우거나 lastReadMessageId를 빼면 방의 마지막 메시지까지 읽은 것으로 한다.
     */
    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<DmReadResponse>> markRead(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @RequestBody(required = false) DmReadRequest request) {
        Long lastReadMessageId = request == null ? null : request.lastReadMessageId();
        return ResponseEntity.ok(ApiResponse.ok(directMessageService.markRead(authUser.id(), roomId, lastReadMessageId)));
    }

    /**
     * 대화 상대 또는 상대의 메시지를 신고한다.
     */
    @PostMapping("/rooms/{roomId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<DmReportResponse>> report(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @Valid @RequestBody DmReportRequest request) {
        DmReportResponse response = dmReportService.report(authUser.id(), roomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
