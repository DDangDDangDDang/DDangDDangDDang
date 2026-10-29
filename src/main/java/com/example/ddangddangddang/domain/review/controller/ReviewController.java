package com.example.ddangddangddang.domain.review.controller;

import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.review.dto.request.ReviewCreateRequest;
import com.example.ddangddangddang.domain.review.service.ReviewService;
import com.example.ddangddangddang.global.common.ApiResponse;
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
    @GetMapping()
    ResponseEntity<ApiResponse<ProductResponse>> createReview(@AuthenticationPrincipal AuthUser authUser,
                                                                    @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reviewService.createReview(authUser.id(),request)));
    }
//    // 구매자 리뷰 수정
//    @GetMapping("/{reviewId}")
//    ResponseEntity<ApiResponse<ProductResponse>> findAddBy(@PathVariable Long productId) {
//        return ResponseEntity.ok(ApiResponse.ok(categoryService.findAddBy(productId)));
//    }
//    // 구매자 리뷰 삭제
//    @PostMapping("/{reviewId}")
//    @ResponseStatus(HttpStatus.CREATED)
//    ResponseEntity<ApiResponse<ProductResponse>> creation(@AuthenticationPrincipal AuthUser authUser,
//                                                          @Valid @RequestBody ProductRequest request) {
//        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(categoryService.creation(authUser.id(), request)));
//    }
//
//    // 판매자 리뷰 목록(상점 리뷰 탭,로그인 없이 조회)
//    @PatchMapping("/sellers/{sellerId}")
//    ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@AuthenticationPrincipal AuthUser authUser,
//                                                               @PathVariable Long productId,
//                                                               @Valid @RequestBody UpdateRequest request) {
//        return ResponseEntity.ok(ApiResponse.ok(categoryService.updateProduct(authUser.id(),productId, request)));
//    }
//
//    // 판맺자 답글 달기,수정
//    @DeleteMapping("/sellers/{reviewId}/reply")
//    @ResponseStatus(HttpStatus.NO_CONTENT)
//    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser authUser,
//                                @PathVariable Long productId){
//        categoryService.delete(authUser.id(),productId);
//        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
//    }
//
//    // 판매자 답글 삭제
//    @DeleteMapping("/sellers/{reviewId}/reply")
//


}
