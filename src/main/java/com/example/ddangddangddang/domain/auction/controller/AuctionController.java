package com.example.ddangddangddang.domain.auction.controller;


import com.example.ddangddangddang.domain.auction.dto.request.AuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.request.AuctionSearchCondition;
import com.example.ddangddangddang.domain.auction.dto.request.LiveAuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionListResponse;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionResponse;
import com.example.ddangddangddang.domain.auction.service.AuctionService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AuctionController {
    private final AuctionService auctionService;

    //경매(일반) 생성
    @PostMapping("/auctions")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<AuctionResponse>> auctionCreate(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AuctionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(auctionService.auctionCreate(authUser.id(), request)));
    }

    @GetMapping("/auctions/{auctionId}")
    ResponseEntity<ApiResponse<AuctionResponse>> findAddBy(
            @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.findAddBy(auctionId)));
    }

    //경매 취소(본인 경매만)
    @PostMapping("/auctions/{auctionId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<Void> cancelAuction(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long auctionId) {
        auctionService.cancelAuction(authUser.id(), auctionId);
        return ResponseEntity.noContent().build();
    }

    // 라이브 중 경매 생성(방송 주인 및, 생성 즉시 AuctionStatus는 ONGOING변환)
    @PostMapping("/lives/{liveId}/auctions")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<AuctionResponse>> createLiveAuction(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveId,
            @Valid @RequestBody LiveAuctionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(auctionService.createLiveAuction(authUser.id(), liveId, request)));
    }

    // 이 방송에 올라간 경매(누구나). 최신순 50개, 끝난 경매도 포함
    @GetMapping("/lives/{liveId}/auctions")
    ResponseEntity<ApiResponse<List<AuctionResponse>>> liveAuctions(
            @PathVariable Long liveId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.liveAuctions(liveId)));
    }

    // 이미 만든 시작 전·진행 중 경매를 이 방송에 올리기(방송 주인, 방송 중). 경매 일정·상태는 그대로
    @PostMapping("/lives/{liveId}/auctions/{auctionId}")
    ResponseEntity<ApiResponse<AuctionResponse>> showOnLive(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveId,
            @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.showOnLive(authUser.id(), liveId, auctionId)));
    }

    // 내 시작 전·진행 중 경매(최신순 50개). 스튜디오에서 방송에 올릴 경매를 고를 때 쓴다
    @GetMapping("/users/me/auctions")
    ResponseEntity<ApiResponse<List<AuctionResponse>>> myOpenAuctions(
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.myOpenAuctions(authUser.id())));
    }

    //상품의 경매 이력(판매자 본인만). 이상품으로 열었던 경매 전부(유찰,취소 포함)
    @GetMapping("/products/{productId}/auctions")
    ResponseEntity<ApiResponse<List<AuctionResponse>>> productAuctionHistory(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long productId){
        return ResponseEntity.ok(ApiResponse.ok(auctionService.productAuctionHistory(authUser.id(), productId)));
    }


    //경매 목록 조회 및 검색(누구나)
    @GetMapping("/auctions")
    ResponseEntity<ApiResponse<Page<AuctionListResponse>>> searchAuctions(
            @ParameterObject @ModelAttribute AuctionSearchCondition condition,
            @ParameterObject @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.searchAuctions(condition, pageable)));
    }

    @GetMapping("/auctions/popular")
    ResponseEntity<ApiResponse<List<AuctionListResponse>>> popularAuctions(
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.popularAuctions(size)));
    }

}
