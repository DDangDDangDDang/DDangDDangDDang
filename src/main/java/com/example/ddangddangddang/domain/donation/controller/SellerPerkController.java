package com.example.ddangddangddang.domain.donation.controller;

import com.example.ddangddangddang.domain.donation.dto.SellerPerkPurchaseRequest;
import com.example.ddangddangddang.domain.donation.dto.SellerPerkResponse;
import com.example.ddangddangddang.domain.donation.dto.SellerShopItemResponse;
import com.example.ddangddangddang.domain.donation.service.SellerPerkService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 판매자 상점: 선물받은 도네이션 포인트로 파워 판매자 등의 혜택을 산다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/seller-perks")
public class SellerPerkController {

    private final SellerPerkService sellerPerkService;

    @GetMapping("/shop")
    public ResponseEntity<ApiResponse<List<SellerShopItemResponse>>> getShop() {
        return ResponseEntity.ok(ApiResponse.ok(sellerPerkService.getShop()));
    }

    /** 아직 끝나지 않은 내 혜택 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<SellerPerkResponse>>> getMine(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(sellerPerkService.getMine(authUser.id())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<SellerPerkResponse>> purchase(@AuthenticationPrincipal AuthUser authUser,
                                                                    @Valid @RequestBody SellerPerkPurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(sellerPerkService.purchase(authUser.id(), request.type())));
    }
}
