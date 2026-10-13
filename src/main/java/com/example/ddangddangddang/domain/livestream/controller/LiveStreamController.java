package com.example.ddangddangddang.domain.livestream.controller;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamTokenResponse;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/live-streams")
public class LiveStreamController {

    private final LiveStreamService liveStreamService;

    @PostMapping
    public ResponseEntity<ApiResponse<LiveStreamResponse>> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody LiveStreamCreateRequest request) {
        LiveStreamResponse response = liveStreamService.create(authUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/{liveStreamId}")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> get(@PathVariable Long liveStreamId) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.get(liveStreamId)));
    }

    /**
     * 판매자가 방송을 시작한다.
     * 응답의 serverUrl + token 으로 클라이언트가 LiveKit에 접속해 송출한다.
     */
    @PostMapping("/{liveStreamId}/start")
    public ResponseEntity<ApiResponse<LiveStreamTokenResponse>> start(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId) {
        LiveStreamTokenResponse response = liveStreamService.start(liveStreamId, authUser.id(), authUser.nickname());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 시청자가 진행 중인 방송에 입장한다. 시청 전용 토큰을 발급한다.
     */
    @PostMapping("/{liveStreamId}/join")
    public ResponseEntity<ApiResponse<LiveStreamTokenResponse>> join(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId) {
        LiveStreamTokenResponse response = liveStreamService.join(liveStreamId, authUser.id(), authUser.nickname());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{liveStreamId}/end")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> end(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.end(liveStreamId, authUser.id())));
    }
}
