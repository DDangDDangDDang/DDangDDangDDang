package com.example.ddangddangddang.domain.shop.controller;

import com.example.ddangddangddang.domain.shop.dto.AuctionBoostRequest;
import com.example.ddangddangddang.domain.shop.dto.AuctionBoostResponse;
import com.example.ddangddangddang.domain.shop.dto.NicknameChangeTicketRequest;
import com.example.ddangddangddang.domain.shop.dto.ShopItemResponse;
import com.example.ddangddangddang.domain.shop.service.ShopService;
import com.example.ddangddangddang.domain.user.dto.MyBadgesResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.entity.NicknameBadge;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 마일리지 상점 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shop")
public class ShopController {

    private final ShopService shopService;

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<ShopItemResponse>>> getItems(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(shopService.getItems(authUser.id())));
    }

    /** 닉네임 변경권을 사면서 바로 닉네임을 바꾼다. 응답은 내 정보 수정과 같다. (바뀐 닉네임이 담긴 새 Access Token) */
    @PostMapping("/nickname-change")
    public ResponseEntity<ApiResponse<MyInfoUpdateResponse>> buyNicknameChange(
            @AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody NicknameChangeTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(shopService.buyNicknameChange(authUser.id(), request.nickname())));
    }

    /** 내 경매를 3시간 동안 경매 목록(최신순) 맨 위에 올린다. (1,000M, 경매당 한 번, 동시 10개) */
    @PostMapping("/auction-boosts")
    public ResponseEntity<ApiResponse<AuctionBoostResponse>> boostAuction(
            @AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody AuctionBoostRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(shopService.boostAuction(authUser.id(), request.auctionId())));
    }

    /** 닉네임 뱃지를 산다. 영구로 가지고 바로 장착된다. 응답은 내 뱃지 (가진 뱃지·장착한 뱃지) */
    @PostMapping("/badges/{badge}")
    public ResponseEntity<ApiResponse<MyBadgesResponse>> buyBadge(
            @AuthenticationPrincipal AuthUser authUser, @PathVariable NicknameBadge badge) {
        return ResponseEntity.ok(ApiResponse.ok(shopService.buyBadge(authUser.id(), badge)));
    }
}
