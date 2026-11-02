package com.example.ddangddangddang.domain.seller.controller;

import com.example.ddangddangddang.domain.seller.dto.SellerApplicationRequest;
import com.example.ddangddangddang.domain.seller.dto.SellerApplicationResponse;
import com.example.ddangddangddang.domain.seller.dto.SellerIdentityRequest;
import com.example.ddangddangddang.domain.seller.dto.SellerIdentityResponse;
import com.example.ddangddangddang.domain.seller.dto.SellerStoreUpdateRequest;
import com.example.ddangddangddang.domain.seller.service.SellerApplicationService;
import com.example.ddangddangddang.domain.seller.service.SellerIdentityService;
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
    private final SellerIdentityService identityService;

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

    /**
     * 휴대폰 본인인증 결과 등록. 화면에서 포트원 본인인증 창을 마친 뒤 그 ID를 보낸다. 판매자 신청 전에 필요하다.
     * 만 19세 미만이면 SELLER_012, 다른 계정이 이미 쓴 명의면 SELLER_013
     */
    @PostMapping("/identity")
    public ResponseEntity<ApiResponse<SellerIdentityResponse>> verifyIdentity(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody SellerIdentityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(identityService.verify(authUser.id(), request.identityVerificationId())));
    }

    /** 내 본인인증. 인증한 적이 없으면 data가 null이다. */
    @GetMapping("/identity")
    public ResponseEntity<ApiResponse<SellerIdentityResponse>> getMyIdentity(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(identityService.getMine(authUser.id())));
    }

    /** 내 판매자 신청. 신청한 적이 없으면 data가 null이다. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> getMine(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getMine(authUser.id())));
    }
}
