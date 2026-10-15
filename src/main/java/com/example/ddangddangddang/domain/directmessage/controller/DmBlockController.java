package com.example.ddangddangddang.domain.directmessage.controller;

import com.example.ddangddangddang.domain.directmessage.dto.DmBlockRequest;
import com.example.ddangddangddang.domain.directmessage.dto.DmBlockResponse;
import com.example.ddangddangddang.domain.directmessage.service.DmBlockService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 1:1 채팅 차단. 차단하면 두 사람 모두 서로에게 메시지를 보낼 수 없다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dm/blocks")
public class DmBlockController {

    private final DmBlockService dmBlockService;

    @PostMapping
    public ResponseEntity<ApiResponse<DmBlockResponse>> block(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody DmBlockRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(dmBlockService.block(authUser.id(), request.userId())));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long userId) {
        dmBlockService.unblock(authUser.id(), userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DmBlockResponse>>> getBlocks(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(dmBlockService.getBlocks(authUser.id())));
    }
}
