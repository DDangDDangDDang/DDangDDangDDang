package com.example.ddangddangddang.domain.auction.controller;

import com.example.ddangddangddang.domain.auction.dto.request.BidRequest;
import com.example.ddangddangddang.domain.auction.dto.response.BidHistoryResponse;
import com.example.ddangddangddang.domain.auction.dto.response.BidResponse;
import com.example.ddangddangddang.domain.auction.dto.response.BuyNowResponse;
import com.example.ddangddangddang.domain.auction.service.BidFacade;
import com.example.ddangddangddang.domain.auction.service.BidService;
import com.example.ddangddangddang.domain.auction.service.BuyNowService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions/{auctionId}")
public class BidController {

    private final BidFacade bidFacade;
    private final BidService bidService;
    private final BuyNowService buyNowService;

    @PostMapping("/bids")
    public ResponseEntity<ApiResponse<BidResponse>> placeBid(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(bidFacade.placeBid(authUser.id(), auctionId, request.amount())));
    }

    @GetMapping("/bids")
    public ResponseEntity<ApiResponse<CursorResponse<BidHistoryResponse>>> getBids(
            @PathVariable Long auctionId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(bidService.getBids(auctionId, cursor, size)));
    }

    @PostMapping("/buy-now")
    public ResponseEntity<ApiResponse<BuyNowResponse>> buyNow(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long auctionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(buyNowService.buyNow(authUser.id(), auctionId)));
    }
}
