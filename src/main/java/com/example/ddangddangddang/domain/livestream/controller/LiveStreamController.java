package com.example.ddangddangddang.domain.livestream.controller;

import com.example.ddangddangddang.domain.livestream.dto.FeaturedAuctionRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCard;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamHomeResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamTokenResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamUpdateRequest;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamHomeService;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamListService;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
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
    private final LiveStreamHomeService liveStreamHomeService;
    private final LiveStreamListService liveStreamListService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<LiveStreamResponse>> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody LiveStreamCreateRequest request) {
        LiveStreamResponse response = liveStreamService.create(authUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /**
     * 방송 목록·검색. status별로 정렬이 다르다. (LIVE 최근 시작순, SCHEDULED 시작 예정 시간이 가까운 순, ENDED 최근 종료순)
     * sellerId를 주면 그 판매자의 방송만, keyword를 주면 제목·설명·판매자 닉네임에 검색어가 들어간 방송만 보여 준다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorResponse<LiveStreamCard>>> list(
            @RequestParam(required = false) LiveStreamStatus status,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamListService.getList(status, sellerId, keyword, cursor, size)));
    }

    /**
     * 홈 화면 방송 목록 (시청자 많은 방송, 시청자 급증 방송). size는 섹션별 개수 (최대 20)
     */
    @GetMapping("/home")
    public ResponseEntity<ApiResponse<LiveStreamHomeResponse>> home(@RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamHomeService.getHome(size)));
    }

    /**
     * 내 종료되지 않은 방송 (대기 중·방송 중). 판매자당 하나이며, 없으면 data가 null이다.
     */
    @GetMapping("/me/active")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> myActive(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.getMyActive(authUser.id())));
    }

    @GetMapping("/{liveStreamId}")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> get(@PathVariable Long liveStreamId) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.get(liveStreamId)));
    }

    /**
     * 판매자가 방송 정보(제목·설명)를 바꾼다. 방송 중에도 바꿀 수 있고, 시청자 화면에 바로 반영된다.
     */
    @PatchMapping("/{liveStreamId}")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> update(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId,
            @Valid @RequestBody LiveStreamUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.update(liveStreamId, authUser.id(), request)));
    }

    /**
     * 판매자가 영상 위에 띄울 경매를 정한다. 한 번에 하나만 띄우고, 시청자 화면에 바로 반영된다.
     */
    @PutMapping("/{liveStreamId}/featured-auction")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> featureAuction(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId,
            @Valid @RequestBody FeaturedAuctionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                liveStreamService.changeFeaturedAuction(liveStreamId, authUser.id(), request.auctionId())));
    }

    /**
     * 영상 위에 띄운 경매를 내린다.
     */
    @DeleteMapping("/{liveStreamId}/featured-auction")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> unfeatureAuction(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.changeFeaturedAuction(liveStreamId, authUser.id(), null)));
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
    /**
     * 송출 중인 방송의 송출 토큰을 다시 발급한다. (판매자 새로고침, 송출 연결 끊김, 토큰 만료)
     * 방송 시작은 LIVE 상태에서 다시 할 수 없으므로 이 API로 다시 접속한다.
     */
    @PostMapping("/{liveStreamId}/publisher-token")
    public ResponseEntity<ApiResponse<LiveStreamTokenResponse>> reissuePublisherToken(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId) {
        LiveStreamTokenResponse response =
                liveStreamService.reissuePublisherToken(liveStreamId, authUser.id(), authUser.nickname());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** 시청 입장. 로그인하지 않았으면 손님으로 입장한다. (SecurityConfig.PUBLIC_POST_APIS) */
    @PostMapping("/{liveStreamId}/join")
    public ResponseEntity<ApiResponse<LiveStreamTokenResponse>> join(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId) {
        LiveStreamTokenResponse response = authUser == null
                ? liveStreamService.joinAsGuest(liveStreamId)
                : liveStreamService.join(liveStreamId, authUser.id(), authUser.nickname());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{liveStreamId}/end")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> end(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.end(liveStreamId, authUser.id())));
    }
}
