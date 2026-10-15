package com.example.ddangddangddang.domain.auction.controller;

import com.example.ddangddangddang.domain.auction.dto.request.AuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.request.LiveAuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionResponse;
import com.example.ddangddangddang.domain.auction.service.AuctionService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
//
//    //카테고리 생성(관리자만)
//    @PostMapping("/admin/categories")
//    ResponseEntity<ApiResponse<AuctionResponse>> creation(@Valid @RequestBody AuctionCreateRequest request){
//        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(auctionService.creation(request)));
//    }
//
//    //카테고리 수정(관리자만)
//    @PatchMapping("/admin/categories/{categoryId}")
//    ResponseEntity<ApiResponse<AuctionResponse>> updateCategory(@PathVariable Long categoryId, @Valid @RequestBody LiveAuctionCreateRequest request){
//        return ResponseEntity.ok(ApiResponse.ok(auctionService.updateCategory(categoryId,request)));
//    }
//
//    //카테고리 삭제(관리자만)
//    @DeleteMapping("/admin/categories/{categoryId}")
//    ResponseEntity<Void> delete(@PathVariable Long categoryId){
//        auctionService.delete(categoryId);
//        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
//    }


}
