package com.example.ddangddangddang.domain.directmessage.controller;

import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmModerationFlagResponse;
import com.example.ddangddangddang.domain.directmessage.service.DmModerationFlagService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 1:1 메시지 AI 검토 확인. /api/v1/admin/** 은 SecurityConfig에서 ADMIN만 접근할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dm/moderation-flags")
public class AdminDmModerationController {

    private final DmModerationFlagService dmModerationFlagService;

    @GetMapping
    public ResponseEntity<ApiResponse<CursorResponse<DmModerationFlagResponse>>> getFlags(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(dmModerationFlagService.getFlags(cursor, size)));
    }

    /**
     * AI가 표시한 대화방의 메시지. 표시가 있는 방만 볼 수 있다.
     */
    @GetMapping("/{flagId}/messages")
    public ResponseEntity<ApiResponse<CursorResponse<DmMessageResponse>>> getFlaggedRoomMessages(
            @PathVariable Long flagId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(dmModerationFlagService.getFlaggedRoomMessages(flagId, cursor, size)));
    }
}
