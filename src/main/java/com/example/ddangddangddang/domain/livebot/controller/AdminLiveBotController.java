package com.example.ddangddangddang.domain.livebot.controller;

import com.example.ddangddangddang.domain.livebot.dto.LiveBotCreateRequest;
import com.example.ddangddangddang.domain.livebot.dto.LiveBotResponse;
import com.example.ddangddangddang.domain.livebot.dto.LiveBotStartRequest;
import com.example.ddangddangddang.domain.livebot.dto.LiveBotVideoUploadRequest;
import com.example.ddangddangddang.domain.livebot.dto.LiveBotVideoUploadResponse;
import com.example.ddangddangddang.domain.livebot.service.LiveBotService;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.user.dto.ProfileImageChangeRequest;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadRequest;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadResponse;
import com.example.ddangddangddang.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자 라이브 봇 관리. /api/v1/admin/** 은 SecurityConfig에서 ADMIN만 통과한다 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/live-bots")
public class AdminLiveBotController {

    private final LiveBotService liveBotService;

    /** 봇 목록 (최근에 만든 봇부터, 켜 둔 방송 포함) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<LiveBotResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(liveBotService.getAll()));
    }

    /** 녹화 영상 업로드 URL */
    @PostMapping("/video-uploads")
    public ResponseEntity<ApiResponse<LiveBotVideoUploadResponse>> createVideoUpload(@Valid @RequestBody LiveBotVideoUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(liveBotService.createVideoUpload(request)));
    }

    /** 봇 만들기 (로그인할 수 없는 판매자 계정 + 녹화 영상) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<LiveBotResponse>> create(@Valid @RequestBody LiveBotCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(liveBotService.create(request)));
    }

    /** 봇 방송 켜기 (녹화 영상 방송을 만들어 바로 시작) */
    @PostMapping("/{botId}/start")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> start(@PathVariable Long botId, @Valid @RequestBody LiveBotStartRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(liveBotService.start(botId, request)));
    }

    /** 봇 방송 끄기 */
    @PostMapping("/{botId}/end")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> end(@PathVariable Long botId) {
        return ResponseEntity.ok(ApiResponse.ok(liveBotService.end(botId)));
    }

    /** 봇 프로필 사진 업로드 URL (1단계). 받은 URL로 올린 뒤 PUT /{botId}/profile-image로 확정한다. */
    @PostMapping("/{botId}/profile-image/upload-url")
    public ResponseEntity<ApiResponse<ProfileImageUploadResponse>> createProfileImageUpload(
            @PathVariable Long botId, @Valid @RequestBody ProfileImageUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(liveBotService.createProfileImageUpload(botId, request)));
    }

    /** 봇 프로필 사진 바꾸기 (2단계) */
    @PutMapping("/{botId}/profile-image")
    public ResponseEntity<ApiResponse<LiveBotResponse>> changeProfileImage(
            @PathVariable Long botId, @Valid @RequestBody ProfileImageChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(liveBotService.changeProfileImage(botId, request.imageKey())));
    }

    /** 봇 프로필 사진 지우기 */
    @DeleteMapping("/{botId}/profile-image")
    public ResponseEntity<ApiResponse<LiveBotResponse>> deleteProfileImage(@PathVariable Long botId) {
        return ResponseEntity.ok(ApiResponse.ok(liveBotService.deleteProfileImage(botId)));
    }

    /** 봇 지우기 (방송 중이면 거절, 영상도 지운다. 계정은 남긴다) */
    @DeleteMapping("/{botId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable Long botId) {
        liveBotService.delete(botId);
        return ResponseEntity.noContent().build();
    }
}
