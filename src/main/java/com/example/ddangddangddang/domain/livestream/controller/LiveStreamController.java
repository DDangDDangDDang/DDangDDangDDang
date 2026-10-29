package com.example.ddangddangddang.domain.livestream.controller;

import com.example.ddangddangddang.domain.livestream.dto.FeaturedAuctionRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCard;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamHomeResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamTokenResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamUpdateRequest;
import com.example.ddangddangddang.domain.livestream.dto.OverlayImageUploadRequest;
import com.example.ddangddangddang.domain.livestream.dto.OverlayImageUploadResponse;
import com.example.ddangddangddang.domain.livestream.dto.OverlayLayoutRequest;
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
     * 판매자가 경매를 영상 위에 띄운다. 최대 2개까지 띄우고(넘으면 409 LIVESTREAM_017), 시청자 화면에 바로 반영된다.
     */
    @PutMapping("/{liveStreamId}/featured-auction")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> featureAuction(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId,
            @Valid @RequestBody FeaturedAuctionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                liveStreamService.featureAuction(liveStreamId, authUser.id(), request.auctionId())));
    }

    /**
     * 판매자가 방송 화면 배치를 정한다. 경매 카드·선물·입찰·낙찰 안내의 위치와 숨김, 직접 넣은 텍스트·이미지 박스를 함께 보낸다.
     * 방송 시작 전에도 정할 수 있고, 시청자 화면에 바로 반영된다.
     */
    @PutMapping("/{liveStreamId}/overlay-layout")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> changeOverlayLayout(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId,
            @Valid @RequestBody OverlayLayoutRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.changeOverlayLayout(liveStreamId, authUser.id(), request)));
    }

    /**
     * 방송 화면 이미지 박스에 넣을 이미지의 업로드 URL을 받는다. 올린 뒤 imageKey를 박스에 넣어 화면 배치를 저장한다.
     * JPG·PNG·WEBP·GIF, 5MB까지. 방송 주인만, 종료 전에만 받을 수 있다.
     */
    @PostMapping("/{liveStreamId}/overlay-images")
    public ResponseEntity<ApiResponse<OverlayImageUploadResponse>> createOverlayImageUpload(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId,
            @Valid @RequestBody OverlayImageUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.createOverlayImageUpload(liveStreamId, authUser.id(), request)));
    }

    /**
     * 영상 위에 띄운 경매를 모두 내린다.
     */
    @DeleteMapping("/{liveStreamId}/featured-auction")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> unfeatureAllAuctions(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.unfeatureAuction(liveStreamId, authUser.id(), null)));
    }

    /**
     * 영상 위에 띄운 경매 하나를 내린다. 띄우지 않은 경매면 아무것도 바뀌지 않는다.
     */
    @DeleteMapping("/{liveStreamId}/featured-auction/{auctionId}")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> unfeatureAuction(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId,
            @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.unfeatureAuction(liveStreamId, authUser.id(), auctionId)));
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

    /**
     * 홈 방송 카드 미리보기 토큰. 다른 참가자에게 보이지 않고 시청자 수에도 들어가지 않는다. 로그인하지 않아도 된다. (SecurityConfig.PUBLIC_POST_APIS)
     * IP당 1분에 60회로 제한한다. (PreviewRateLimitInterceptor)
     */
    @PostMapping("/{liveStreamId}/preview")
    public ResponseEntity<ApiResponse<LiveStreamTokenResponse>> preview(@PathVariable Long liveStreamId) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.preview(liveStreamId)));
    }

    @PostMapping("/{liveStreamId}/end")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> end(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveStreamId) {
        return ResponseEntity.ok(ApiResponse.ok(liveStreamService.end(liveStreamId, authUser.id())));
    }
}
