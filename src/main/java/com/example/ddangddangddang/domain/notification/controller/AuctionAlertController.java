package com.example.ddangddangddang.domain.notification.controller;

import com.example.ddangddangddang.domain.auction.dto.response.AuctionListResponse;
import com.example.ddangddangddang.domain.notification.dto.AuctionAlertFilter;
import com.example.ddangddangddang.domain.notification.dto.AuctionAlertResponse;
import com.example.ddangddangddang.domain.notification.service.AuctionAlertService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
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
@RequestMapping("/api/v1")
public class AuctionAlertController {

    private final AuctionAlertService auctionAlertService;

    /**
     * 내가 알림 받기를 한 경매 (최근에 받기로 한 것부터, 경매 목록 카드와 같은 형식)
     * filter: ACTIVE(시작 전·진행 중, 기본) / ENDED(낙찰·유찰·취소. 끝난 지 7일이 지나면 목록에서 정리된다)
     */
    @GetMapping("/users/me/auction-alerts")
    public ResponseEntity<ApiResponse<CursorResponse<AuctionListResponse>>> getMine(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "ACTIVE") AuctionAlertFilter filter,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(auctionAlertService.getMine(authUser.id(), filter, cursor, size)));
    }

    @GetMapping("/auctions/{auctionId}/alert")
    public ResponseEntity<ApiResponse<AuctionAlertResponse>> get(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionAlertService.get(authUser.id(), auctionId)));
    }

    @PutMapping("/auctions/{auctionId}/alert")
    public ResponseEntity<ApiResponse<AuctionAlertResponse>> subscribe(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionAlertService.subscribe(authUser.id(), auctionId)));
    }

    @DeleteMapping("/auctions/{auctionId}/alert")
    public ResponseEntity<ApiResponse<AuctionAlertResponse>> unsubscribe(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionAlertService.unsubscribe(authUser.id(), auctionId)));
    }
}
