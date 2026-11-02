package com.example.ddangddangddang.domain.review.controller;

import com.example.ddangddangddang.domain.review.dto.request.ReviewCreateRequest;
import com.example.ddangddangddang.domain.review.dto.request.ReviewReplyRequest;
import com.example.ddangddangddang.domain.review.dto.request.ReviewUpdateRequest;
import com.example.ddangddangddang.domain.review.dto.response.ReviewResponse;
import com.example.ddangddangddang.domain.review.service.ReviewService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    // 구매자 리뷰 작성
    @PostMapping()
    ResponseEntity<ApiResponse<ReviewResponse>> createReview(@AuthenticationPrincipal AuthUser authUser,
                                                                           @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reviewService.createReview(authUser.id(),request)));
    }
    // 구매자 리뷰 수정
    @PatchMapping("/{reviewId}")
    ResponseEntity<ApiResponse<ReviewResponse>> updateReview(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long reviewId,
                                                              @Valid @RequestBody ReviewUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.updateReview(authUser.id(),reviewId,request)));
    }
    // 구매자 리뷰 삭제
    @DeleteMapping("/{reviewId}")
    ResponseEntity<Void> deleteReview(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long reviewId) {
        reviewService.deleteReview(authUser.id(), reviewId);
        return ResponseEntity.noContent().build();
    }

    // 판매자 리뷰 목록(상점 리뷰 탭,로그인 없이 조회)
    @GetMapping()
    ResponseEntity<ApiResponse<CursorResponse<ReviewResponse>>> getSellerReviews(@RequestParam Long sellerId,
                                                                                @RequestParam(required = false) Long cursor,
                                                                                @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getSellerReviews(sellerId,cursor, size)));
    }

    // 판맺자 답글 달기,수정
    @PutMapping("/{reviewId}/reply")
    ResponseEntity<ApiResponse<ReviewResponse>> replyReview(@AuthenticationPrincipal AuthUser authUser,
                                                            @PathVariable Long reviewId,
                                                            @Valid @RequestBody ReviewReplyRequest request){
        return ResponseEntity.ok(ApiResponse.ok(reviewService.replyReview(authUser.id(),reviewId,request)));
    }

    // 판매자 답글 삭제
    @DeleteMapping("/{reviewId}/reply")
    ResponseEntity<Void> deleteReply(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long reviewId) {
        reviewService.deleteReply(authUser.id(), reviewId);
        return ResponseEntity.noContent().build();
    }

    // 내 주무의 리뷰 조회(주문 화면에서 수정,삭제할 리뷰 찾기)
    @GetMapping("/orders/{orderId}")
    ResponseEntity<ApiResponse<ReviewResponse>> getMyOrderReview(@AuthenticationPrincipal AuthUser authUser,
                                                                 @PathVariable Long orderId){
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getMyOrderReview(authUser.id(),orderId)));
    }


}
