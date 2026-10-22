package com.example.ddangddangddang.domain.notification.controller;

import com.example.ddangddangddang.domain.notification.dto.AuctionAlertResponse;
import com.example.ddangddangddang.domain.notification.service.AuctionAlertService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 경매 알림 받기. 받기로 한 경매가 시작·마감 10분 전·취소·종료되면 알림함(+실시간, 브라우저 알림)으로 알린다.
 * 입찰한 경매는 받기로 하지 않아도 상위 입찰·마감 임박·취소·종료 알림이 온다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions/{auctionId}/alert")
public class AuctionAlertController {

    private final AuctionAlertService auctionAlertService;

    @GetMapping
    public ResponseEntity<ApiResponse<AuctionAlertResponse>> get(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionAlertService.get(authUser.id(), auctionId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AuctionAlertResponse>> subscribe(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionAlertService.subscribe(authUser.id(), auctionId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<AuctionAlertResponse>> unsubscribe(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionAlertService.unsubscribe(authUser.id(), auctionId)));
    }
}
