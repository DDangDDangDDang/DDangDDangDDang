package com.example.ddangddangddang.domain.seller.controller;

import com.example.ddangddangddang.domain.seller.dto.SellerApplicationRequest;
import com.example.ddangddangddang.domain.seller.dto.SellerApplicationResponse;
import com.example.ddangddangddang.domain.seller.dto.SellerStoreUpdateRequest;
import com.example.ddangddangddang.domain.seller.service.SellerApplicationService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/seller-applications")
public class SellerApplicationController {

    private final SellerApplicationService applicationService;

    /** 판매자 신청. 반려된 신청이 있으면 고쳐 다시 낸다. 관리자가 승인하면 판매자 인증 마크가 붙는다. */
    @PostMapping
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> apply(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody SellerApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.apply(authUser.id(), request)));
    }

    /** 인증받은 판매자의 상점 이름·소개 수정. 다시 심사받지 않는다. 인증받지 않았으면 SELLER_008 */
    @PutMapping("/me/store")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> updateStore(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody SellerStoreUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.updateStore(authUser.id(), request)));
    }

    /** 내 판매자 신청. 신청한 적이 없으면 data가 null이다. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> getMine(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getMine(authUser.id())));
    }
}
