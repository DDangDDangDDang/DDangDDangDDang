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
    ResponseEntity<ApiResponse<AuctionResponse>> auctionCreate(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AuctionCreateRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(auctionService.auctionCreate(authUser.id(),request))) ;
    }

    @GetMapping("/auctions/{auctionId}")
    ResponseEntity<ApiResponse<AuctionResponse>> findAddBy(
            @PathVariable Long auctionId){
        return ResponseEntity.ok(ApiResponse.ok(auctionService.findAddBy(auctionId))) ;
    }

    //경매 취소(본인 경매만)
    @PostMapping("/auctions/{auctionId}/cancel")
    ResponseEntity<Void> cancelAuction(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long auctionId){
        auctionService.cancelAuction(authUser.id(), auctionId);
        return ResponseEntity.noContent().build();
    }

    // 라이브 중 경매 생성(방송 주인 및, 생성 즉시 AuctionStatus는 ONGOING변환)
    @PostMapping("/lives/{liveId}/auctions")
    ResponseEntity<ApiResponse<AuctionResponse>> createLiveAuction(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long liveId,
            @Valid @RequestBody LiveAuctionCreateRequest request
    ){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(auctionService.createLiveAuction(authUser.id(),liveId,request)));
    }

    //경매 목록 조회 및 검색(누구나)
    @GetMapping("/auctions")
    ResponseEntity<ApiResponse<Page<AuctionListResponse>>> searchAuctions(
            @ModelAttribute AuctionSearchCondition condition,
            @PageableDefault(size = 30) Pageable pageable){
        return ResponseEntity.ok(ApiResponse.ok(auctionService.searchAuctions(condition,pageable)));
    }
    @GetMapping("/auctions/popular")
    ResponseEntity<ApiResponse<List<AuctionListResponse>>> popularAuctions(
            @RequestParam(defaultValue = "10") int size){
        return ResponseEntity.ok(ApiResponse.ok(auctionService.popularAuctions(size)));
    }

}
