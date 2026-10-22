package com.example.ddangddangddang.domain.seller.controller;

import com.example.ddangddangddang.domain.seller.dto.SellerAuctionResponse;
import com.example.ddangddangddang.domain.seller.dto.SellerProfileResponse;
import com.example.ddangddangddang.domain.seller.dto.SubscribedSellerResponse;
import com.example.ddangddangddang.domain.seller.dto.SubscriptionResponse;
import com.example.ddangddangddang.domain.seller.service.SellerProfileService;
import com.example.ddangddangddang.domain.seller.service.SubscriptionService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 판매자 프로필과 구독.
 * 프로필·경매 기록은 로그인하지 않아도 볼 수 있다. (SecurityConfig.PUBLIC_GET_APIS)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class SellerController {

    private final SellerProfileService profileService;
    private final SubscriptionService subscriptionService;

    /** 판매자 프로필 (기본 탭). 로그인했으면 내가 구독 중인지(subscribed)도 알려 준다. */
    @GetMapping("/sellers/{sellerId}/profile")
    public ResponseEntity<ApiResponse<SellerProfileResponse>> getProfile(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getProfile(sellerId, authUser == null ? null : authUser.id())));
    }

    /** 판매자 경매 기록. 최근 경매부터, 다음 페이지는 nextCursor를 cursor로 넘긴다. */
    @GetMapping("/sellers/{sellerId}/auctions")
    public ResponseEntity<ApiResponse<CursorResponse<SellerAuctionResponse>>> getAuctions(
            @PathVariable Long sellerId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getAuctions(sellerId, cursor, size)));
    }

    /** 판매자를 구독한다. 그 판매자가 방송을 시작하면 알림이 온다. 이미 구독 중이면 그대로 둔다. */
    @PutMapping("/sellers/{sellerId}/subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> subscribe(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.subscribe(authUser.id(), sellerId)));
    }

    /** 구독을 취소한다. 구독하지 않았어도 성공한다. */
    @DeleteMapping("/sellers/{sellerId}/subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> unsubscribe(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.unsubscribe(authUser.id(), sellerId)));
    }

    /** 내가 구독한 판매자 (방송 중인 판매자가 위) */
    @GetMapping("/users/me/subscriptions")
    public ResponseEntity<ApiResponse<List<SubscribedSellerResponse>>> getMySubscriptions(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.getMySubscriptions(authUser.id())));
    }
}
