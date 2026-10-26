package com.example.ddangddangddang.domain.auction.controller;

import com.example.ddangddangddang.domain.auction.dto.request.BidRequest;
import com.example.ddangddangddang.domain.auction.dto.response.BidHistoryResponse;
import com.example.ddangddangddang.domain.auction.dto.response.BidResponse;
import com.example.ddangddangddang.domain.auction.dto.response.BuyNowResponse;
import com.example.ddangddangddang.domain.auction.dto.response.MyBidResponse;
import com.example.ddangddangddang.domain.auction.service.BidFacade;
import com.example.ddangddangddang.domain.auction.service.BidQueryService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions/{auctionId}")
public class BidController {

    private final BidFacade bidFacade;
    private final BidQueryService bidQueryService;
    private final BuyNowService buyNowService;

    // @ResponseStatus는 API 명세에 201로 표시하려고 붙인다 (ResponseEntity.status만으로는 명세가 200으로 나온다)
    @PostMapping("/bids")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<BidResponse>> placeBid(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(bidFacade.placeBid(authUser.id(), auctionId, request.amount())));
    }

    /** 입찰 기록 (최신순). 로그인하지 않아도 볼 수 있다. 입찰자는 가린 닉네임으로, 로그인했으면 본인 입찰을 표시한다 */
    @GetMapping("/bids")
    public ResponseEntity<ApiResponse<CursorResponse<BidHistoryResponse>>> getBids(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long auctionId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        Long viewerId = authUser == null ? null : authUser.id();
        return ResponseEntity.ok(ApiResponse.ok(bidQueryService.getBids(auctionId, viewerId, cursor, size)));
    }

    /** 이 경매에서 내 입찰 상태 (최고 입찰 중 / 밀림 / 낙찰 / 낙찰 실패) */
    @GetMapping("/bids/me")
    public ResponseEntity<ApiResponse<MyBidResponse>> getMyBid(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(bidQueryService.getMyBid(auctionId, authUser.id())));
    }

    @PostMapping("/buy-now")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<BuyNowResponse>> buyNow(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long auctionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(buyNowService.buyNow(authUser.id(), auctionId)));
    }
}
