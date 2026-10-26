package com.example.ddangddangddang.domain.donation.controller;

import com.example.ddangddangddang.domain.donation.dto.DonationPointHistoryResponse;
import com.example.ddangddangddang.domain.donation.dto.DonationRequest;
import com.example.ddangddangddang.domain.donation.dto.DonationResponse;
import com.example.ddangddangddang.domain.donation.dto.DonationStatusResponse;
import com.example.ddangddangddang.domain.donation.dto.DonationWalletResponse;
import com.example.ddangddangddang.domain.donation.service.DonationPointService;
import com.example.ddangddangddang.domain.donation.service.DonationService;
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
 * 도네이션 포인트: 내 지갑·내역, 방송 중인 판매자에게 선물하기
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class DonationController {

    private final DonationPointService donationPointService;
    private final DonationService donationService;

    /** 내 도네이션 포인트 (선물할 포인트·받은 포인트·곧 사라질 포인트·이번 달 적립) */
    @GetMapping("/donation-points/wallet")
    public ResponseEntity<ApiResponse<DonationWalletResponse>> getWallet(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(donationPointService.getWallet(authUser.id())));
    }

    /** 내 도네이션 포인트 내역 (최근부터) */
    @GetMapping("/donation-points/histories")
    public ResponseEntity<ApiResponse<CursorResponse<DonationPointHistoryResponse>>> getHistories(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(donationPointService.getHistories(authUser.id(), cursor, size)));
    }

    /** 선물 창 정보: 이 방송의 판매자에게 지금 얼마까지 선물할 수 있는지 */
    @GetMapping("/live-streams/{liveStreamId}/donations/me")
    public ResponseEntity<ApiResponse<DonationStatusResponse>> getStatus(@AuthenticationPrincipal AuthUser authUser,
                                                                         @PathVariable Long liveStreamId) {
        return ResponseEntity.ok(ApiResponse.ok(donationService.getStatus(liveStreamId, authUser.id())));
    }

    /**
     * 방송 중인 판매자에게 도네이션 포인트를 선물한다.
     * 성공하면 방송 채팅 토픽(/topic/live-streams/{id}/chat)으로 type이 DONATION인 메시지가 나간다. (방송 화면 이펙트)
     */
    @PostMapping("/live-streams/{liveStreamId}/donations")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<DonationResponse>> gift(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long liveStreamId,
                                                              @Valid @RequestBody DonationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(donationService.gift(liveStreamId, authUser.id(), authUser.nickname(), request)));
    }
}
